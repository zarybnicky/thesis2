package montuno.interpreter

import pretty.*

fun <A> par(c: Boolean, x: Doc<A>): Doc<A> =
    if (c) "(".text() + x + ")".text() else x

fun Term.pretty(ns: NameEnv = NameEnv(), p: Boolean = false): Doc<Nothing> = when (this) {
    is TMeta -> "?${meta.i}.${meta.j}".text()
    is TTop -> MontunoPure.top[lvl].name.text()
    is TLocal -> ns[ix].text()
    TIrrelevant -> "Irr".text()
    is TU -> "*".text()
    is TNat -> n.toString().text()
    is TForeign -> "[$lang|$eval|".text() + ty.pretty(ns, false) + "]".text()
    is TLet -> {
        val d = listOf(
            ":".text() spaced ty.pretty(ns, false),
            "=".text() spaced v.pretty(ns, false),
        ).vCat().align()
        val r = listOf(
            "let $n".text() spaced d,
            "in".text() spaced tm.pretty(ns + n, false)
        ).vCat().align()
        par(p, r)
    }
    is TApp -> par(p, l.pretty(ns, true) + " ".text() + when (icit) {
        Icit.Expl -> "{".text() + r.pretty(ns, false) + "}".text()
        Icit.Impl -> r.pretty(ns, true)
    })
    is TFun -> par(p, l.pretty(ns, l !is TApp) + " → ".text() + r.pretty(ns, false))
    is TLam -> {
        var x = ns.fresh(n)
        var nsLocal = ns + x
        var rest = tm
        var b = when (icit) {
            Icit.Expl -> "λ $x".text()
            Icit.Impl -> "λ {$x}".text()
        }
        while (rest is TLam) {
            x = nsLocal.fresh(rest.n)
            nsLocal += x
            rest = rest.tm
            b += when (icit) {
                Icit.Expl -> " $x".text()
                Icit.Impl -> " {$x}".text()
            }
        }
        par(p, b + ". ".text() + rest.pretty(ns, false))
    }
    is TPi -> {
        var x = ns.fresh(n)
        var b = when (icit) {
            Icit.Expl -> "($x : ".text() + arg.pretty(ns, false) + ")".text()
            Icit.Impl -> "{$x : ".text() + arg.pretty(ns, false) + "}".text()
        }
        var nsLocal = ns + x
        var rest = tm
        while (rest is TPi) {
            x = nsLocal.fresh(rest.n)
            b += when {
                x == "_" -> " → ".text() + rest.arg.pretty(nsLocal, p)
                rest.icit == Icit.Expl -> " ($x : ".text() + rest.arg.pretty(nsLocal, p) + ")".text()
                else -> " {$x : ".text() + rest.arg.pretty(nsLocal, p) + "}".text()
            }
            nsLocal += x
            rest = rest.tm
        }
        par(p, b + " → ".text() + rest.pretty(ns, p))
    }
}
