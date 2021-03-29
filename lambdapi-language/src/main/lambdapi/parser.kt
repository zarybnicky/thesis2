package lambdapi

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream

fun LambdapiParser.TermContext.toAst(): Raw = when (this) {
    is LambdapiParser.LetContext -> RLet(name.toAst(), type.toAst(), tm.toAst(), body.toAst())
    is LambdapiParser.LamContext -> args.foldRight(body.toAst(), { l, r -> RLam(l.text, r) })
    is LambdapiParser.PiContext -> dom.foldRight(cod.toAst(), { l, r -> RPi(l.text, kind.toAst(), r) })
    is LambdapiParser.AppContext -> {
        val expr = spine.map { it.toAst() }.reduce { l, r -> RApp(l, r) }
        if (rest == null) expr else RPi("_", expr, rest.toAst())
    }
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun LambdapiParser.BinderContext.toAst(): String = when (this) {
    is LambdapiParser.IdentContext -> IDENT().text
    is LambdapiParser.HoleContext -> "_"
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun LambdapiParser.AtomContext.toAst(): Raw = when (this) {
    is LambdapiParser.RecContext -> rec.toAst()
    is LambdapiParser.VarContext -> RVar(IDENT().text)
    is LambdapiParser.StarContext -> RStar
    is LambdapiParser.NatContext -> RNat
    is LambdapiParser.LitNatContext -> RLitNat(NAT().text.toInt())
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun parseString(input: String): Raw =
    LambdapiParser(CommonTokenStream(LambdapiLexer(CharStreams.fromString(input)))).term().toAst()
