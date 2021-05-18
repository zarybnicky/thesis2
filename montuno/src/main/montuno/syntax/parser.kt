package montuno.syntax

import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.source.SourceSection
import montuno.*
import montuno.interpreter.Icit
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode

interface WithPos {
    val loc: Loc
}
sealed class Loc {
    object Unavailable : Loc() { override fun toString(): String = "?" }
    data class Range(val start: Int, val length: Int) : Loc() { override fun toString(): String = "$start:$length" }
    data class Line(val line: Int) : Loc() { override fun toString(): String = "l$line" }

    fun string(source: String): String = when (this) {
        is Unavailable -> "<unavailable>"
        is Range -> source.subSequence(start, start + length).toString()
        is Line -> source.lineSequence().elementAt(line)
    }
    fun section(src: Source?): SourceSection? = when (this) {
        is Unavailable -> src?.createUnavailableSection()
        is Range -> src?.createSection(start, length)
        is Line -> src?.createSection(line)
    }
}

sealed class NameOrIcit
object NIImpl : NameOrIcit() { override fun toString(): String = "NIImpl" }
object NIExpl : NameOrIcit() { override fun toString(): String = "NIExpl" }
data class NIName(val n: String) : NameOrIcit()

enum class Pragma { Elaborate, Normalize, Type, NormalType, ParseOnly, Nothing, Reset, WholeProgram, Symbols }

fun ParserRuleContext.range() = Loc.Range(this.start.startIndex, this.stop.stopIndex - this.start.startIndex + 1)

fun parsePreSyntax(input: String): List<TopLevel> =
    MontunoParser(CommonTokenStream(MontunoLexer(CharStreams.fromString(input)))).file().toAst()

fun parsePreSyntax(input: Source): List<TopLevel> = parsePreSyntax(input.characters.toString())

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
    is MontunoParser.PragmaContext -> RTerm(range(), toPragma(cmd.text), target?.toAst())
    is MontunoParser.ExprContext -> RTerm(range(), Pragma.Nothing, term().toAst())
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun toPragma(s: String) = when (s) {
    "TYPE" -> Pragma.Type
    "NORMAL-TYPE" -> Pragma.NormalType
    "ELABORATE" -> Pragma.Elaborate
    "NORMALIZE" -> Pragma.Normalize
    "PARSE" -> Pragma.ParseOnly
    "WHOLE-PROGRAM" -> Pragma.WholeProgram
    "SYMBOLS" -> Pragma.Symbols
    "RESET" -> Pragma.Reset
    else -> throw UnsupportedOperationException("Unrecognized command $s")
}

fun MontunoParser.TermContext.toAst(): PreTerm = when (this) {
    is MontunoParser.LetContext -> RLet(range(), id.toAst(), type.toAst(), defn.toAst(), body.toAst())
    is MontunoParser.LamContext -> rands.foldRight(body.toAst()) { l, r ->
        val (n, ni) = l.toAst()
        RLam(range(), n, ni, r)
    }
    is MontunoParser.PiContext -> spine.foldRight(body.toAst()) { l, r -> l.toAst()(r) }
    is MontunoParser.AppContext -> {
        val expr = rands.fold(rator.toAst()) { l, r -> r.toAst()(l) }
        if (body == null) expr else RFun(range(), expr, body.toAst())
    }
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.ArgContext.toAst(): (PreTerm) -> PreTerm = when (this) {
    is MontunoParser.ArgImplContext -> { t -> RApp(range(), if (IDENT() != null) NIName(IDENT().text) else NIImpl, t, term().toAst())}
    is MontunoParser.ArgExplContext -> { t -> RApp(range(), NIExpl, t, atom().toAst()) }
    is MontunoParser.ArgStopContext -> { t -> RStopMeta(range(), t) }
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.PiBindContext.toAst(): (PreTerm) -> PreTerm = { t -> when (this) {
    is MontunoParser.PiExplContext -> {
        val type = this.type.toAst()
        ids.foldRight(t, { l, r -> RPi(range(), l.text, Icit.Expl, type, r) })
    }
    is MontunoParser.PiImplContext -> {
        val type = this.type?.toAst() ?: RHole(range())
        ids.foldRight(t, { l, r -> RPi(range(), l.text, Icit.Impl, type, r) })
    }
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
} }

fun MontunoParser.LamBindContext.toAst(): Pair<String, NameOrIcit> = when (this) {
    is MontunoParser.LamExplContext -> binder().toAst() to NIExpl
    is MontunoParser.LamImplContext -> binder().toAst() to NIImpl
    is MontunoParser.LamNameContext -> binder().toAst() to NIName(IDENT().text)
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.AtomContext.toAst(): PreTerm = when (this) {
    is MontunoParser.RecContext -> term().toAst()
    is MontunoParser.VarContext -> RVar(range(), IDENT().text)
    is MontunoParser.HoleContext -> RHole(range())
    is MontunoParser.StarContext -> RU(range())
    is MontunoParser.NatContext -> RNat(range(), NAT().text.toInt())
    is MontunoParser.ForeignContext -> RForeign(range(), IDENT().text, FOREIGN().text, term().toAst())
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.BinderContext.toAst(): String = when (this) {
    is MontunoParser.BindContext -> IDENT().text
    is MontunoParser.IrrelContext -> "_"
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}