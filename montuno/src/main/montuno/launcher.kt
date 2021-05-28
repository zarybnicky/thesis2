package montuno

import org.graalvm.launcher.AbstractLanguageLauncher
import org.graalvm.options.OptionCategory
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotAccess
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import org.jline.reader.Candidate
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.reader.impl.DefaultParser
import org.jline.terminal.TerminalBuilder
import java.io.IOException
import java.nio.file.Paths
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.system.exitProcess

class Launcher : AbstractLanguageLauncher() {
    companion object {
        @JvmStatic fun main(args: Array<String>): Unit = Launcher().launch(args)
    }

    private var stdin = false
    private var programArgs: Array<String> = emptyArray()
    private var versionAction: VersionAction = VersionAction.None
    private var currentLang: String = languageId
    private var currentFile: String? = null
    private var initialCommand: String? = null

    override fun getDefaultLanguages(): Array<String> = arrayOf("montuno", "montuno-pure") // "js","r","ruby"};
    override fun getLanguageId() = "montuno" // default engine

    override fun launch(contextBuilder: Context.Builder) {
        contextBuilder.arguments(languageId, programArgs)
        contextBuilder.allowPolyglotAccess(PolyglotAccess.ALL)
        contextBuilder.build().use { ctx ->
            runVersionAction(versionAction, ctx.engine)
            try {
                if (currentFile != null || stdin || initialCommand != null) evalNonInteractive(ctx)
                else readEvalPrint(ctx)
                exitProcess(0)
            } catch (e: PolyglotException) {
                handlePolyglotException(e)
                exitProcess(-1)
            }
        }
    }

    private fun evalNonInteractive(ctx: Context) {
        val source: Source? = try {
            when {
                currentFile != null -> Source.newBuilder(currentLang, Paths.get(currentFile!!).toFile()).build()
                stdin -> Source.newBuilder(currentLang, System.`in`.bufferedReader(), "<stdin>").build()
                else -> null
            }
        } catch (e: IOException) {
            println("Error loading file '$currentFile' (${e.message})")
            exitProcess(-1)
        }
        if (source != null) {
            var v = ctx.eval(source)
            if (v.canExecute()) v = v.execute()
            if (!v.isNull) println(v)
        }
        val cmd = initialCommand
        if (cmd != null) {
            var v = ctx.eval(processCommand(ctx, cmd))
            if (v.canExecute()) v = v.execute()
            if (!v.isNull) println(v)
        }
    }

    private fun readEvalPrint(ctx: Context) {
        Logger.getLogger("org.jline").level = Level.FINEST

        val reader = LineReaderBuilder.builder()
            .terminal(TerminalBuilder.builder().jna(true).build())
            .parser(DefaultParser().escapeChars(null).regexVariable(null).regexCommand(null))
            .completer { _, _, candidates ->
                ctx.getBindings(currentLang).memberKeys.forEach { candidates.add(Candidate(it)) }
            }
            .build()
        while (true) {
            try {
                val source = reader.readLine("Mt> ").let {
                    if (it.startsWith(":")) {
                        processCommand(ctx, it)
                    } else {
                        Source.create(currentLang, it)
                    }
                }
                if (source != null) {
                    var v = ctx.eval(source)
                    if (v.canExecute()) v = v.execute()
                    if (!v.isNull) println(v)
                }
            } catch (e: UserInterruptException) {
            } catch (e: EndOfFileException) {
                return
            } catch (e: PolyglotException) {
                handlePolyglotException(e)
            }
        }
    }

    private fun processCommand(ctx: Context, input: String): Source? {
        val res = input.split(' ', limit = 2)
        val wrapCmd = { cmd: String -> if (res.size < 2) {
            println("Command requires argument")
            null
        } else Source.newBuilder(currentLang, "{-# $cmd ${res[1]} #-}", "<stdin>").build() }
        return when (res[0]) {
            ":quit" -> exitProcess(0)
            ":help" -> {
                println("""
                Available commands:
                :list - lists currently loaded symbols
                :print - prints out the elaboration state
                :load - discards current state and loads a file
                :reload - discards current state and reloads current file
                :parse - prints out parse tree of an expression
                :raw - prints out the raw inferred term and type
                :elaborate - pretty-prints the result of elaborating an expression
                :normalize - evaluates the expression into a normal form
                :type - prints the inferred type
                :normalType - normalized the inferred type
                :builtin - loads a built-in command (:builtin ALL loads them all, see :list; fix is only available for Truffle) 
                :engine - switches the engine between montuno-pure and montuno (Truffle-based)
            """.trimIndent())
                null
            }
            ":elaborate" -> wrapCmd("ELABORATE")
            ":normalize" -> wrapCmd("NORMALIZE")
            ":type" -> wrapCmd("TYPE")
            ":normalType" -> wrapCmd("NORMAL_TYPE")
            ":parse" -> wrapCmd("PARSE")
            ":builtin" -> wrapCmd("BUILTIN")
            ":raw" -> wrapCmd("RAW")
            ":print" -> Source.create(currentLang, "{-# WHOLE_PROGRAM #-}")
            ":list" -> { ctx.getBindings(currentLang).memberKeys.forEach { println(it) }; null }
            ":reload" -> {
                ctx.eval(currentLang, "{-# RESET #-}")
                Source.newBuilder(currentLang, Paths.get(currentFile!!).toFile()).build()
            }
            ":load" -> {
                ctx.eval(currentLang, "{-# RESET #-}")
                if (res.size < 2) null
                else try {
                    ctx.eval(currentLang, "{-# RESET #-}")
                    currentFile = res[1]
                    Source.newBuilder(currentLang, Paths.get(currentFile!!).toFile()).build()
                } catch (e: IOException) {
                    println("Error loading file '$currentFile' (${e.message})")
                    null
                }
            }
            ":engine" -> {
                if (res.size < 2) {
                    println(currentLang.capitalize())
                    null
                } else if (res[1] !in listOf("montuno", "montuno-pure")) {
                    println("Invalid language")
                    null
                } else {
                    ctx.eval(currentLang, "{-# RESET #-}")
                    currentLang = res[1]
                    if (currentFile != null) Source.newBuilder(currentLang, Paths.get(currentFile!!).toFile()).build()
                    else null
                }
            }
            else -> {
                println("Invalid command")
                null
            }
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
                "-" -> stdin = true
                "--show-version" -> versionAction = VersionAction.PrintAndContinue
                "--version" -> versionAction = VersionAction.PrintAndExit
                "--pure" -> currentLang = "montuno-pure"
                "--truffle" -> currentLang = "montuno"
                "--elaborate" -> initialCommand = ":elaborate ${iterator.next()}"
                "--normalize" -> initialCommand = ":normalize ${iterator.next()}"
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

        if (currentFile == null && iterator.hasNext()) currentFile = iterator.next()
        val programArgumentsList = arguments.subList(iterator.nextIndex(), arguments.size)
        programArgs = programArgumentsList.toTypedArray()
        return unrecognizedOptions
    }

    override fun collectArguments(args: MutableSet<String>) {
        args.addAll(listOf("--show-version", "--version", "--pure", "--truffle"))
    }

    override fun printHelp(_maxCategory: OptionCategory) {
        println("Usage: montuno [OPTION]... [FILE]|- [PROGRAM ARGS]")
        println()
        println("Options:")
        println("\t--show-version\tprint the version and continue")
        println("\t--version\tprint the version and exit")
        println("\t--pure\tselect the non-Truffle interpreter")
        println("\t--truffle\tselect the Truffle interpreter")
        println("\t--elaborate EXPR\tprint the result of elaboration")
        println("\t--normalize EXPR\telaborate and normalize an expression")
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
}
