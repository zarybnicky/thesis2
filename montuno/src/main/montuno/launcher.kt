package montuno

import montuno.truffle.MontunoLanguage
import org.graalvm.launcher.AbstractLanguageLauncher
import org.graalvm.options.OptionCategory
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import org.jline.reader.*
import org.jline.terminal.TerminalBuilder
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import java.util.ArrayList
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.system.exitProcess
import montuno.interpreter.simple.nfMain as nfMainSimple
import montuno.interpreter.meta.nfMain as nfMainMeta

const val defn1 = """
    id : {A} -> A -> A = \x. x.
    const : {A B} -> A -> B -> A = \x _. x.
    const' : {A B} (C : *) -> (B -> A) -> A -> (A -> C) -> A = \_ _ a _. a.
"""

const val defn2 = """
    Nat : * = (n : *) → (n → n) → n → n.
    zero : Nat = λ n s z. z.
    suc : Nat → Nat = λ a n s z. s (a n s z).
    n2 : Nat = λ n s z. s (s z).
    n5 : Nat = λ n s z. s (s (s (s (s z)))).
"""

const val expr0 = "foo : * = *. bar : * = id id."

const val expr1 = "%normalize id ((A B : *) -> A -> B -> A) const"

const val expr2 = """
    Nat' : * = (N : *) -> (N -> N) -> N -> N.
    five : Nat' = \N s z. s (s (s (s (s z)))).
    add  : Nat' -> Nat' -> Nat' = \a b N s z. a N s (b N s z).
    mul  : Nat' -> Nat' -> Nat' = \a b N s z. a N (b N s) z.
    ten      : Nat' = add five five.
    hundred  : Nat' = mul ten ten.
    thousand : Nat' = mul ten hundred.
    %nf thousand.
"""

const val ex3 = "%nf id Nat 5"

const val expr4 = """
    Vec : * → Nat → * = λ a n. (V : Nat → *) → V 0 → ((n : Nat) → a → V n → V (n + 1)) → V n.
    vnil : (a : *) → Vec a 0 = λ V n c. n.
    vcons : (a n : *) → a → Vec a n → Vec a (n + 1) = λ a as V n c. c a (as V n c).
    %nf vec1 = vcons true (vcons false (vcons true vnil)).
"""

enum class Interpreter { Simple, Meta, Truffle }

class Launcher : AbstractLanguageLauncher() {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Launcher().launch(args)
        }
    }

    private var programArgs: Array<String> = emptyArray()
    private var versionAction: VersionAction = VersionAction.None
    private var interpreter: Interpreter = Interpreter.Truffle
    private var file: File? = null

    override fun getDefaultLanguages(): Array<String> = arrayOf(languageId) // "js","r","ruby"};
    override fun getLanguageId() = MontunoLanguage.LANGUAGE_ID

    override fun launch(contextBuilder: Context.Builder) {
        contextBuilder.arguments(languageId, programArgs)
        val src: Source? = try {
            if (file != null) Source.newBuilder(languageId, file).build() else null
        } catch (e: IOException) {
            println("Error loading file '$file' (${e.message})")
            exitProcess(-1)
        }
        try {
            when (interpreter) {
                Interpreter.Simple -> TODO("simple nfMain")
                Interpreter.Meta -> TODO("meta nfMain")
                Interpreter.Truffle -> contextBuilder.build().use { ctx ->
                    runVersionAction(versionAction, ctx.engine)
                    if (file !== null) {
                        val v = ctx.eval(src)
                        println(if (v.canExecute()) v.execute() else v) //TODO: as Int?
                    } else repl { s ->
                        val v = ctx.eval(Source.create(languageId, s))
                        println(if (v.canExecute()) v.execute() else v)
                    }
                }
            }
            exitProcess(0)
        } catch (e: PolyglotException) {
            handlePolyglotException(e)
        }
    }

    override fun preprocessArguments(arguments: MutableList<String>, polyglotOptions: MutableMap<String, String>): List<String> {
        val unrecognizedOptions = ArrayList<String>()
        val iterator = arguments.listIterator()
        while (iterator.hasNext()) {
            val option = iterator.next()
            if (option.length < 2 || !option.startsWith("-")) {
                iterator.previous()
                break
            }
            when (option) {
                "--" -> { }
                "--show-version" -> versionAction = VersionAction.PrintAndContinue
                "--version" -> versionAction = VersionAction.PrintAndExit
                "--simple" -> interpreter = Interpreter.Simple
                "--meta" -> interpreter = Interpreter.Meta
                "--truffle" -> interpreter = Interpreter.Truffle
                else -> {
                    val equalsIndex = option.indexOf('=')
                    val argument = when {
                        equalsIndex > 0 -> option.substring(equalsIndex + 1)
                        iterator.hasNext() -> iterator.next()
                        else -> null
                    }
                    unrecognizedOptions.add(option)
                    if (equalsIndex < 0 && argument != null) iterator.previous()
                }
            }
        }

        if (file == null && iterator.hasNext()) file = Paths.get(iterator.next()).toFile()
        val programArgumentsList = arguments.subList(iterator.nextIndex(), arguments.size)
        programArgs = programArgumentsList.toTypedArray()
        return unrecognizedOptions
    }

    override fun printHelp(_maxCategory: OptionCategory) {
        println("Usage: montuno [OPTION]... [FILE] [PROGRAM ARGS]")
        println()
        println("Options:")
        println("\t--show-version\tprint the version and continue")
        println("\t--version\tprint the version and exit")
        println("\t--simple\tselect the non-Truffle interpreter (expressions only)")
        println("\t--meta\tselect the non-Truffle interpreter w/metas")
        println("\t--truffle\tselect the Truffle interpreter")
    }

    override fun collectArguments(args: MutableSet<String>) {
        args.addAll(listOf("--show-version", "--version", "--simple", "--meta", "--truffle"))
    }
}

fun handlePolyglotException(e: PolyglotException) {
    if (e.isExit) exitProcess(e.exitStatus)
    val stackTrace = e.polyglotStackTrace.toMutableList()
    if (!e.isInternalError) {
        val iterator = stackTrace.listIterator(stackTrace.size)
        while (iterator.hasPrevious()) {
            if (iterator.previous().isHostFrame) iterator.remove()
            else break
        }
    }
    println(if (e.isHostException) e.asHostException().toString() else e.message)
    stackTrace.forEach { println("  at $it") }
    exitProcess(-1)
}

// https://github.com/ValV/testline/blob/master/src/main/kotlin/testline/Example.kt
fun repl(eval: (String) -> Unit) {
    Logger.getLogger("org.jline").level = Level.SEVERE
    val builder = TerminalBuilder.builder().jansi(true)
    val completer: Completer? = null
    val terminal = builder.build()
    val reader = LineReaderBuilder.builder()
        .terminal(terminal)
        .completer(completer)
        .build()
    while (true) {
        try {
            eval(reader.readLine("> ", "", null as MaskingCallback?, null))
        } catch (e: UserInterruptException) {
        } catch (e: EndOfFileException) {
            return
        } catch (e: PolyglotException) {
            handlePolyglotException(e)
        }
    }
}