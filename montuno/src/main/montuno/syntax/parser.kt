package montuno.syntax

import montuno.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext

fun ParserRuleContext.range() = Loc.Range(this.start.startIndex, this.stop.stopIndex - this.start.startIndex + 1)

fun parseString(input: String): Raw =
    MontunoParser(CommonTokenStream(MontunoLexer(CharStreams.fromString(input)))).term().toAst()

fun MontunoParser.TermContext.toAst(): Raw = when (this) {
    is MontunoParser.LetContext -> range() with RLet(name.toAst(), type.toAst(), tm.toAst(), body.toAst())
    is MontunoParser.LamContext -> range() with args.foldRight(body.toAst(), { l, r -> RLam(l.text, r) })
    is MontunoParser.PiContext -> range() with dom.foldRight(cod.toAst(), { l, r -> RPi(l.text, kind.toAst(), r) })
    is MontunoParser.AppContext -> {
        val expr = spine.map { it.toAst() }.reduce { l, r -> RApp(l, r) }
        range() with if (rest == null) expr else RPi("_", expr, rest.toAst())
    }
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.BinderContext.toAst(): String = when (this) {
    is MontunoParser.IdentContext -> IDENT().text
    is MontunoParser.HoleContext -> "_"
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun MontunoParser.AtomContext.toAst(): Raw = when (this) {
    is MontunoParser.RecContext -> range() with rec.toAst()
    is MontunoParser.VarContext -> range() with RVar(IDENT().text)
    is MontunoParser.StarContext -> range() with RStar
    is MontunoParser.NatContext -> range() with RNat
    is MontunoParser.LitNatContext -> range() with RLitNat(NAT().text.toInt())
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}
