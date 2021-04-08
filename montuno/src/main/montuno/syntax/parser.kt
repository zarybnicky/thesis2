package montuno.syntax

import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.source.SourceSection
import montuno.*
import montuno.interpreter.Icit
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext

interface WithPos {
    val loc: Loc
}
sealed class Loc {
    object Unavailable : Loc()
    data class Range(val start: Int, val length: Int) : Loc()
    data class Line(val line: Int) : Loc()

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

sealed class TopLevel : WithPos
data class RDecl(override val loc: Loc, val n: String, val ty: PreTerm) : TopLevel()
data class RDefn(override val loc: Loc, val n: String, val ty: PreTerm?, val tm: PreTerm) : TopLevel()
data class RElab(override val loc: Loc, val tm: PreTerm) : TopLevel()
data class RNorm(override val loc: Loc, val tm: PreTerm) : TopLevel()

sealed class PreTerm : WithPos
data class RVar(override val loc: Loc, val n: String) : PreTerm()
data class RForeign(override val loc: Loc, val lang: String, val n: String) : PreTerm()
data class RLitNat(override val loc: Loc, val n: Int) : PreTerm()
data class RApp(override val loc: Loc, val arg: PreTerm, val body: PreTerm) : PreTerm()
data class RLam(override val loc: Loc, val arg: String, val body: PreTerm) : PreTerm()
data class RStar(override val loc: Loc) : PreTerm()
data class RNat(override val loc: Loc) : PreTerm()
data class RPi(override val loc: Loc, val icit: Icit, val arg: String, val ty: PreTerm, val body: PreTerm) : PreTerm()
data class RLet(override val loc: Loc, val n: String, val ty: PreTerm, val bind: PreTerm, val body: PreTerm) : PreTerm()
// class RIf(val ifE: Raw, val thenE: Raw, val elseE: Raw) : Raw()

fun ParserRuleContext.range() = Loc.Range(this.start.startIndex, this.stop.stopIndex - this.start.startIndex + 1)

fun parseExpr(input: String): PreTerm =
    MontunoParser(CommonTokenStream(MontunoLexer(CharStreams.fromString(input)))).term().toAst()
fun parseModule(input: String): List<TopLevel> =
    MontunoParser(CommonTokenStream(MontunoLexer(CharStreams.fromString(input)))).file().toAst()

fun MontunoParser.FileContext.toAst(): List<TopLevel> = decls.map { it.toAst() }

fun MontunoParser.TopContext.toAst(): TopLevel = when (this) {
    is MontunoParser.DeclContext -> RDecl(range(), id.text, type.toAst())
    is MontunoParser.DefnContext -> RDefn(range(), id.text, type?.toAst(), body.toAst())
    is MontunoParser.ElabContext -> RElab(range(), term().toAst())
    is MontunoParser.NormContext -> RNorm(range(), term().toAst())
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.TermContext.toAst(): PreTerm = when (this) {
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

fun MontunoParser.AtomContext.toAst(): PreTerm = when (this) {
    is MontunoParser.RecContext -> rec.toAst()
    is MontunoParser.VarContext -> RVar(range(), IDENT().text)
    is MontunoParser.StarContext -> RStar(range())
    is MontunoParser.NatContext -> RNat(range())
    is MontunoParser.LitNatContext -> RLitNat(range(), NAT().text.toInt())
    is MontunoParser.ForeignContext -> RForeign(range(), lang.text, id.text)
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}
