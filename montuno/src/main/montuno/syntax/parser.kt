package montuno.syntax

import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.source.SourceSection
import montuno.MetaInsertion
import montuno.MontunoLexer
import montuno.MontunoParser
import montuno.interpreter.VPi
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext

enum class Icit {
    Expl,
    Impl
}

enum class Pragma {
    ELABORATE,
    NORMALIZE,
    TYPE,
    NORMAL_TYPE,
    RAW,
    PARSE,
    NOTHING,
    RESET,
    PRINT,
    SYMBOLS,
    BUILTIN,
}

sealed class ArgInfo(override val loc: Loc) : WithLoc {
    fun match(v: VPi): Boolean = when (this) {
        is Unnamed -> n == v.icit
        is Named -> n == v.name
    }
    val icit: Icit? get() = when (this) {
        is Unnamed -> n
        is Named -> null
    }
    val name: String? get() = when (this) {
        is Unnamed -> null
        is Named -> n
    }
    val metaInsertion: MetaInsertion get() = when (this) {
        is Unnamed -> if (n == Icit.Impl) MetaInsertion.No else MetaInsertion.Yes
        is Named -> MetaInsertion.UntilName(n)
    }
}
data class Unnamed(val n: Icit) : ArgInfo(Loc.Unavailable)
data class Named(override val loc: Loc, val n: String) : ArgInfo(loc)

sealed class Binding {
    val name: String? get() = when (this) {
        is Bound -> n
        Unbound -> null
    }
}
object Unbound : Binding()
data class Bound(val n: String) : Binding()
interface WithLoc {
    val loc: Loc
}

fun ParserRuleContext.range() = Loc.Range(this.start.startIndex, this.stop.stopIndex - this.start.startIndex + 1)
sealed class Loc {
    object Unavailable : Loc() { override fun toString(): String = "?" }
    data class Range(val start: Int, val length: Int) : Loc() { override fun toString(): String = "$start:$length" }

    fun string(source: String): String = when (this) {
        is Unavailable -> "<unavailable>"
        is Range -> source.subSequence(start, start + length).toString()
    }
    fun section(src: Source?): SourceSection? = when (this) {
        is Unavailable -> src?.createUnavailableSection()
        is Range -> src?.createSection(start, length)
    }
}


fun parsePreSyntax(input: String): List<TopLevel> =
    MontunoParser(CommonTokenStream(MontunoLexer(CharStreams.fromString(input)))).file().toAst()

fun parsePreSyntax(input: Source): List<TopLevel> = parsePreSyntax(input.characters.toString())

// for Core onlyy
fun parsePreSyntaxExpr(input: String): PreTerm {
    val src = parsePreSyntax(input)
    val last = src.last()
    if (last !is RTerm) throw RuntimeException("expression must be last")
    var root: PreTerm = last.tm!!
    for (l in src.reversed()) {
        root = when (l) {
            is RDefn -> RLet(l.loc, l.n, l.ty ?: RHole(Loc.Unavailable), l.tm, root)
            else -> throw RuntimeException("only definitions supported")
        }
    }
    return root
}

fun MontunoParser.FileContext.toAst(): List<TopLevel> = decls.map { it.toAst() }

fun MontunoParser.TopContext.toAst(): TopLevel = when (this) {
    is MontunoParser.DeclContext -> RDecl(range(), id.text, type.toAst())
    is MontunoParser.DefnContext -> RDefn(range(), id.text, type?.toAst(), defn.toAst())
    is MontunoParser.PragmaContext -> RTerm(range(), Pragma.valueOf(cmd.text), target?.toAst())
    is MontunoParser.ExprContext -> RTerm(range(), Pragma.NOTHING, term().toAst())
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.TermContext.toAst(): PreTerm {
    val vals = listOf(lambda().range() to lambda().toAst()).plus(tuple.map { it.range() to it.toAst() })
    val init = vals.last()
    return vals.dropLast(1).foldRight(init.second) { l, r -> RPair(l.first, l.second, r) }
}

fun MontunoParser.LambdaContext.toAst(): PreTerm = when (this) {
    is MontunoParser.LamContext -> rands.foldRight(body.toAst()) { l, r ->
        val (n, ni) = l.toAst()
        RLam(range(), n, ni, r)
    }
    is MontunoParser.LetTypeContext -> RLet(range(), IDENT().text, type.toAst(), defn.toAst(), body.toAst())
    is MontunoParser.LetContext -> RLet(range(), IDENT().text, null, defn.toAst(), body.toAst())
    is MontunoParser.PiContext -> spine.foldRight(body.toAst()) { l, r -> l.toAst()(r) }
    is MontunoParser.FunContext -> RPi(range(), Unbound, Icit.Expl, sigma().toAst(), body.toAst())
    is MontunoParser.LamTermContext -> sigma().toAst()
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.SigmaContext.toAst(): PreTerm = when (this) {
    is MontunoParser.SgNamedContext -> RSg(range(), Bound(binder().text), type.toAst(), body.toAst())
    is MontunoParser.SgAnonContext -> RSg(range(), Unbound, type.toAst(), body.toAst())
    is MontunoParser.SigmaTermContext -> app().toAst()
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.AppContext.toAst(): PreTerm = args.fold(proj().toAst()) { l, r -> r.toAst()(l) }

fun MontunoParser.ProjContext.toAst(): PreTerm = when (this) {
    is MontunoParser.ProjNamedContext -> RProjF(range(), proj().toAst(), IDENT().text)
    is MontunoParser.ProjFstContext -> RProj1(range(), proj().toAst())
    is MontunoParser.ProjSndContext -> RProj2(range(), proj().toAst())
    is MontunoParser.ProjTermContext -> atom().toAst()
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.ArgContext.toAst(): (PreTerm) -> PreTerm = when (this) {
    is MontunoParser.ArgImplContext -> { t ->
        val arg = if (IDENT() != null) Named(range(), IDENT().text) else Unnamed(Icit.Impl)
        RApp(range(), arg, t, term().toAst())
    }
    is MontunoParser.ArgExplContext -> { t -> RApp(range(), Unnamed(Icit.Expl), t, proj().toAst()) }
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.PiBinderContext.toAst(): (PreTerm) -> PreTerm = { t -> when (this) {
    is MontunoParser.PiExplContext -> {
        val type = type.toAst()
        ids.foldRight(t, { l, r -> RPi(range(), Bound(l.text), Icit.Expl, type, r) })
    }
    is MontunoParser.PiImplContext -> {
        val type = type?.toAst() ?: RHole(range())
        ids.foldRight(t, { l, r -> RPi(range(), Bound(l.text), Icit.Impl, type, r) })
    }
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
} }

fun MontunoParser.LamBindContext.toAst(): Pair<ArgInfo, Binding> = when (this) {
    is MontunoParser.LamExplContext -> Unnamed(Icit.Expl) to binder().toAst()
    is MontunoParser.LamImplContext -> Unnamed(Icit.Impl) to binder().toAst()
    is MontunoParser.LamNameContext -> Named(range(), IDENT().text) to binder().toAst()
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.AtomContext.toAst(): PreTerm = when (this) {
    is MontunoParser.RecContext -> term().toAst()
    is MontunoParser.VarContext -> RVar(range(), IDENT().text)
    is MontunoParser.HoleContext -> RHole(range())
    is MontunoParser.StarContext -> RU(range())
    is MontunoParser.NatContext -> RNat(range(), NAT().text.toInt())
//    is MontunoParser.ForeignContext -> RForeign(range(), IDENT().text, FOREIGN().text, term().toAst())
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.BinderContext.toAst(): Binding = when (this) {
    is MontunoParser.BindContext -> Bound(IDENT().text)
    is MontunoParser.IrrelContext -> Unbound
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}