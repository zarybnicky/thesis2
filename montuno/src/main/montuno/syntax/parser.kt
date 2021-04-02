package montuno.syntax

import montuno.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext

enum class Icit {Expl, Impl}

sealed class RawTop(val loc: Loc)
class RDecl(loc: Loc, val n: String, val ty: Raw) : RawTop(loc)
class RDefn(loc: Loc, val n: String, val ty: Raw?, val tm: Raw) : RawTop(loc)
class RElab(loc: Loc, val tm: Raw) : RawTop(loc)

sealed class Raw(val loc: Loc)
class RVar(loc: Loc, val n: String) : Raw(loc)
class RLitNat(loc: Loc, val n: Int) : Raw(loc)
class RApp(loc: Loc, val arg: Raw, val body: Raw) : Raw(loc)
class RLam(loc: Loc, val arg: String, val body: Raw) : Raw(loc)
class RStar(loc: Loc) : Raw(loc)
class RNat(loc: Loc) : Raw(loc)
class RPi(loc: Loc, val icit: Icit, val arg: String, val ty: Raw, val body: Raw) : Raw(loc)
class RLet(loc: Loc, val n: String, val ty: Raw, val bind: Raw, val body: Raw) : Raw(loc)
// class RIf(val ifE: Raw, val thenE: Raw, val elseE: Raw) : Raw()

sealed class Loc {
    object Unavailable : Loc();
    data class Range(val start: Int, val length: Int) : Loc()
    data class Line(val line: Int) : Loc()

    fun string(source: String): String = when (this) {
        is Unavailable -> "<unavailable>"
        is Range -> source.subSequence(start, start + length).toString()
        is Line -> source.lineSequence().elementAt(line)
    }
}

fun ParserRuleContext.range() = Loc.Range(this.start.startIndex, this.stop.stopIndex - this.start.startIndex + 1)

fun parseExpr(input: String): Raw =
    MontunoParser(CommonTokenStream(MontunoLexer(CharStreams.fromString(input)))).term().toAst()
fun parseModule(input: String): List<RawTop> =
    MontunoParser(CommonTokenStream(MontunoLexer(CharStreams.fromString(input)))).file().toAst()

fun MontunoParser.FileContext.toAst(): List<RawTop> = decls.map { it.toAst() }

fun MontunoParser.TopContext.toAst(): RawTop = when (this) {
    is MontunoParser.DeclContext -> RDecl(range(), id.text, type.toAst())
    is MontunoParser.DefnContext -> RDefn(range(), id.text, type?.toAst(), body.toAst())
    is MontunoParser.ElabContext -> RElab(range(), term().toAst())
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.TermContext.toAst(): Raw = when (this) {
    is MontunoParser.LetContext -> RLet(range(), name.toAst(), type.toAst(), tm.toAst(), body.toAst())
    is MontunoParser.LamContext -> args.foldRight(body.toAst(), { l, r -> RLam(range(), l.text, r) })
    is MontunoParser.PiExplContext -> dom.foldRight(cod.toAst(), { l, r -> RPi(range(), Icit.Expl, l.text, kind.toAst(), r) })
    is MontunoParser.PiImplContext -> dom.foldRight(cod.toAst(), { l, r -> RPi(range(), Icit.Impl, l.text, kind?.toAst() ?: RVar(Loc.Unavailable, "_"), r) })
    is MontunoParser.AppContext -> {
        val expr = spine.map { it.toAst() }.reduce { l, r -> RApp(range(), l, r) }
        if (rest == null) expr else RPi(range(), Icit.Impl,"_", expr, rest.toAst())
    }
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.BinderContext.toAst(): String = when (this) {
    is MontunoParser.IdentContext -> IDENT().text
    is MontunoParser.HoleContext -> "_"
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.AtomContext.toAst(): Raw = when (this) {
    is MontunoParser.RecContext -> rec.toAst()
    is MontunoParser.VarContext -> RVar(range(), IDENT().text)
    is MontunoParser.StarContext -> RStar(range())
    is MontunoParser.NatContext -> RNat(range())
    is MontunoParser.LitNatContext -> RLitNat(range(), NAT().text.toInt())
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}