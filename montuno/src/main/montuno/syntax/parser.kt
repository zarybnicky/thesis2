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

sealed class NameOrIcit
object NIImpl : NameOrIcit()
object NIExpl : NameOrIcit()
data class NIName(val n: String) : NameOrIcit()

typealias Program = List<TopLevel>

sealed class TopLevel : WithPos
data class RDecl(override val loc: Loc, val n: String, val ty: PreTerm) : TopLevel()
data class RDefn(override val loc: Loc, val n: String, val ty: PreTerm?, val tm: PreTerm) : TopLevel()
data class RElab(override val loc: Loc, val tm: PreTerm) : TopLevel()
data class RNorm(override val loc: Loc, val tm: PreTerm) : TopLevel()

sealed class PreTerm : WithPos

data class RVar (override val loc: Loc, val n: String) : PreTerm()
data class RApp (override val loc: Loc, val ni: NameOrIcit, val rator: PreTerm, val rand: PreTerm) : PreTerm()
data class RLam (override val loc: Loc, val n: String, val ni: NameOrIcit, val body: PreTerm) : PreTerm()
data class RFun (override val loc: Loc, val l: PreTerm, val r: PreTerm) : PreTerm()
data class RPi  (override val loc: Loc, val n: String, val icit: Icit, val type: PreTerm, val body: PreTerm) : PreTerm()
data class RLet (override val loc: Loc, val n: String, val type: PreTerm, val defn: PreTerm, val body: PreTerm) : PreTerm()
data class RU   (override val loc: Loc) : PreTerm()
data class RHole(override val loc: Loc) : PreTerm()

data class RNat(override val loc: Loc, val n: Int) : PreTerm()
data class RForeign(override val loc: Loc, val lang: String, val eval: String, val type: PreTerm) : PreTerm()
data class RStopMeta(override val loc: Loc, val body: PreTerm) : PreTerm()

// data class RIf(val ifE: Raw, val thenE: Raw, val elseE: Raw) : PreTerm()

fun ParserRuleContext.range() = Loc.Range(this.start.startIndex, this.stop.stopIndex - this.start.startIndex + 1)

fun parseExpr(input: String): PreTerm =
    MontunoParser(CommonTokenStream(MontunoLexer(CharStreams.fromString(input)))).term().toAst()
fun parseModule(input: String): List<TopLevel> =
    MontunoParser(CommonTokenStream(MontunoLexer(CharStreams.fromString(input)))).file().toAst()

fun MontunoParser.FileContext.toAst(): List<TopLevel> = decls.map { it.toAst() }

fun MontunoParser.TopContext.toAst(): TopLevel = when (this) {
    is MontunoParser.DeclContext -> RDecl(range(), id.text, type.toAst())
    is MontunoParser.DefnContext -> RDefn(range(), id.text, type?.toAst(), defn.toAst())
    is MontunoParser.ElabContext -> RElab(range(), term().toAst())
    is MontunoParser.NormContext -> RNorm(range(), term().toAst())
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.TermContext.toAst(): PreTerm = when (this) {
    is MontunoParser.LetContext -> RLet(range(), id.toAst(), type.toAst(), defn.toAst(), body.toAst())
    is MontunoParser.LamContext -> rands.foldRight(body.toAst()) { l, r ->
        val (n, ni) = l.toAst()
        RLam(range(), n, ni, r)
    }
    is MontunoParser.PiContext -> spine.foldRight(body.toAst()) { l, r -> l.toAst()(r) }
    is MontunoParser.AppContext -> {
        val expr = rands.foldRight(rator.toAst()) { l, r -> l.toAst()(r) }
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