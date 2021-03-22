package lambdapi

import guru.nidi.graphviz.attribute.Rank
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Graphviz
import guru.nidi.graphviz.model.Factory.graph
import guru.nidi.graphviz.model.Factory.node
import guru.nidi.graphviz.model.Node
import me.tomassetti.kllvm.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.graalvm.word.Pointer

sealed class Raw
class TVar(val ref: String) : Raw()
class TLam(val args: Array<Pair<String, Raw?>>, val body: Raw) : Raw()
class TApp(val f: Raw, val args: Array<Raw>) : Raw()
class TLet(val n: String, val ty: Raw, val ex: Raw, val bd: Raw) : Raw()

// class TIf(val ifE: Raw, val thenE: Raw, val elseE: Raw) : Raw()
// class TLitNat(val num: Int) : Raw()
class TArr(val l: Raw, val r: Raw) : Raw()

val fresh = mutableMapOf<String, Int>()
fun mkFresh(ident: String): String {
    val x = fresh[ident]
    return if (x == null) {
        fresh[ident] = 0
        ident
    } else {
        fresh[ident] = x + 1
        "$ident ($x)"
    }
}

fun Raw.pretty(): Node = when (this) {
    is TVar -> node(mkFresh(ref))
    is TLam -> node("\\" + args.joinToString(" ") { it.first }).link(args.mapNotNull { it.second?.pretty() }).link(body.pretty())
    is TLet -> node("let $n : ? = ? in ?").link(ty.pretty(), ex.pretty(), bd.pretty())
    is TApp -> node(mkFresh("Ap")).link(f.pretty()).link(args.map { it.pretty() })
    is TArr -> node(mkFresh("->")).link(l.pretty(), r.pretty())
}

fun LambdapiParser.TermContext.toAst(): Raw = when (this) {
    is LambdapiParser.LetContext -> TLet(name.text, type.toAst(), tm.toAst(), body.toAst())
    is LambdapiParser.LamContext -> TLam(args.map { it.toAst() }.toTypedArray(), body.toAst())
    is LambdapiParser.AppContext -> TApp(`fun`.toAst(), args.map { it.toAst() }.toTypedArray())
    is LambdapiParser.ExpContext -> exp.toAst()
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun LambdapiParser.BindContext.toAst(): Pair<String, Raw?> = if (name != null) {
    Pair(name.text, type.toAst())
} else {
    Pair(IDENT().text, null)
}

fun LambdapiParser.InfixContext.toAst(): Raw = when (this) {
    is LambdapiParser.RecContext -> rec.toAst()
    is LambdapiParser.ArrContext -> when (rest) {
        null -> TVar(IDENT().text)
        else -> TArr(TVar(IDENT().text), rest.toAst())
    }
    else -> throw UnsupportedOperationException(javaClass.canonicalName)
}

fun parseString(input: String): Raw =
    LambdapiParser(CommonTokenStream(LambdapiLexer(CharStreams.fromString(input)))).term().toAst()

val collatz =
    "fixNatF (\\(f : Nat -> Nat) (x : Nat) -> if eq x 1 then x else f (printId (if (eq (mod x 2) 0) then div x 2 else plus (mult 3 x) 1))) 11111"

fun main() = println(
    Graphviz.fromGraph(
        graph().directed()
            .graphAttr().with(Rank.dir(Rank.RankDir.TOP_TO_BOTTOM))
            .with(parseString(collatz).pretty())
    ).render(Format.DOT)
)

// $ grun lambdapi.Lambdapi term -tree
// <- fixNatF (\(f : Nat -> Nat) (x : Nat) -> if eq x 1 then x else f (printId (if (eq (mod x 2) 0) then div x 2 else plus (mult 3 x) 1))) 11111
// -> (term
// (infix fixNatF)
// (infix
//  (term
//   "\\"
//   (bind (f : (term (infix Nat -> (infix Nat)))))
//   (bind (x : (term (infix Nat))))
//   ->
//   (term
//    if
//    (term (infix eq) (infix x) (infix 1))
//    then
//    (term (infix x))
//    else
//    (term
//     (infix f)
//     (infix
//      (term
//       (infix printId)
//       (infix
//        (term
//         if
//         (term
//          (infix
//           (term
//            (infix eq)
//            (infix (term (infix mod) (infix x) (infix 2)))
//            (infix 0))
//           ))
//         then
//         (term (infix div) (infix x) (infix 2))
//         else
//         (term
//          (infix plus)
//          (infix (term (infix mult) (infix 3) (infix x)))
//          (infix 1)))
//        ))
//      )))
//   )) (infix 11111))

fun generateSum() {
    val EXIT_CODE_OK = 0
    val EXIT_CODE_WRONG_PARAMS = 1
    val N_PARAMS_EXPECTED = 2
    val STRING_TYPE = Pointer(I8Type)

    val module = ModuleBuilder()
    val mainFunction = module.createMainFunction()

    val atoiDeclaration = FunctionDeclaration("atoi", I32Type, listOf(), varargs = true)
    module.addDeclaration(atoiDeclaration)
    module.addDeclaration(FunctionDeclaration("printf", I32Type, listOf(STRING_TYPE), varargs = true))

    val okParamsBlock = mainFunction.createBlock("okParams")
    val koParamsBlock = mainFunction.createBlock("koParams")

    val comparisonResult = mainFunction.tempValue(Comparison(ComparisonType.Equal,
        mainFunction.paramReference(0), IntConst(N_PARAMS_EXPECTED + 1, I32Type)))
    mainFunction.addInstruction(IfInstruction(comparisonResult.reference(), okParamsBlock, koParamsBlock))

    // OK Block : convert to int, sum, and print
    val aAsStringPtr = okParamsBlock.tempValue(GetElementPtr(STRING_TYPE, mainFunction.paramReference(1), IntConst(1, I64Type)))
    val aAsString = okParamsBlock.load(aAsStringPtr.reference())
    val aAsInt = okParamsBlock.tempValue(CallWithBitCast(atoiDeclaration, aAsString))
    val bAsStringPtr = okParamsBlock.tempValue(GetElementPtr(STRING_TYPE, mainFunction.paramReference(1), IntConst(2, I64Type)))
    val bAsString = okParamsBlock.load(bAsStringPtr.reference())
    val bAsInt = okParamsBlock.tempValue(CallWithBitCast(atoiDeclaration, bAsString))
    val sum = okParamsBlock.tempValue(IntAddition(aAsInt.reference(), bAsInt.reference()))
    okParamsBlock.addInstruction(Printf(mainFunction.stringConstForContent("Result: %d\n").reference(), sum.reference()))
    okParamsBlock.addInstruction(ReturnInt(EXIT_CODE_OK))

    // KO Block : error message and exit
    koParamsBlock.addInstruction(Printf(mainFunction.stringConstForContent("Please specify two arguments").reference()))
    koParamsBlock.addInstruction(ReturnInt(EXIT_CODE_WRONG_PARAMS))

    println(module.IRCode())
    println(module.IRCode().trim() == """@stringConst0 = private unnamed_addr constant [12 x i8] c"Result: %d\0A\00"
@stringConst1 = private unnamed_addr constant [29 x i8] c"Please specify two arguments\00"



declare i32 @atoi(...)
declare i32 @printf(i8*, ...)

define i32 @main(i32, i8**) {
    
    ; unnamed block
    %tmpValue0 = icmp eq i32 %0, 3
    br i1 %tmpValue0, label %okParams, label %koParams

    okParams:
    %tmpValue1 = getelementptr inbounds i8*, i8** %1, i64 1
    %tmpValue2 = load i8*, i8** %tmpValue1
    %tmpValue3 = call i32 (i8*, ...) bitcast (i32 (...)* @atoi to i32 (i8*, ...)*)(i8* %tmpValue2)
    %tmpValue4 = getelementptr inbounds i8*, i8** %1, i64 2
    %tmpValue5 = load i8*, i8** %tmpValue4
    %tmpValue6 = call i32 (i8*, ...) bitcast (i32 (...)* @atoi to i32 (i8*, ...)*)(i8* %tmpValue5)
    %tmpValue7 = add i32 %tmpValue3, %tmpValue6
    call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([12 x i8], [12 x i8]* @stringConst0, i32 0, i32 0), i32 %tmpValue7)
    ret i32 0

    koParams:
    call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([29 x i8], [29 x i8]* @stringConst1, i32 0, i32 0))
    ret i32 1

}""")
}
// llc -filetype=obj example.ll
// clang example.o -o example