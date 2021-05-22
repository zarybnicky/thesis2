package montuno.interpreter

import montuno.Icit
import montuno.interpreter.scope.NameEnv
import pretty.*

fun <A> par(c: Boolean, x: Doc<A>): Doc<A> =
    if (c) "(".text() + x + ")".text() else x

fun Term.pretty(ns: NameEnv, p: Boolean = false): Doc<Nothing> = when (this) {
    is TMeta -> "?${meta.i}.${meta.j}".text()
    is TTop -> slot.name.text()
    is TLocal -> ns[ix].text()
    TIrrelevant -> "Irr".text()
    is TUnit -> "*".text()
    is TNat -> n.toString().text()
    is TForeign -> "[$lang|$code|".text() + type.pretty(ns, false) + "]".text()
    is TLet -> {
        val d = listOf(
            ":".text() spaced type.pretty(ns, false),
            "=".text() spaced bound.pretty(ns, false),
        ).vCat().align()
        val r = listOf(
            "let $name".text() spaced d,
            "in".text() spaced body.pretty(ns + name, false)
        ).vCat().align()
        par(p, r)
    }
    is TApp -> par(p, lhs.pretty(ns, true) + " ".text() + when (icit) {
        Icit.Impl -> "{".text() + rhs.pretty(ns, false) + "}".text()
        Icit.Expl -> rhs.pretty(ns, true)
    })
    is TFun -> par(p, lhs.pretty(ns, lhs !is TApp) + " → ".text() + rhs.pretty(ns, false))
    is TLam -> {
        var x = ns.fresh(name)
        var nsLocal = ns + x
        var b = when (icit) {
            Icit.Expl -> "λ $x".text()
            Icit.Impl -> "λ {$x}".text()
        }
        var rest = body
        while (rest is TLam) {
            x = nsLocal.fresh(rest.name)
            nsLocal += x
            b += when (rest.icit) {
                Icit.Expl -> " $x".text()
                Icit.Impl -> " {$x}".text()
            }
            rest = rest.body
        }
        par(p, b + ". ".text() + rest.pretty(nsLocal, false))
    }
    is TPi -> {
        var x = ns.fresh(name)
        var b = when {
            x == "_" -> bound.pretty(ns, false)
            icit == Icit.Impl -> "{$x : ".text() + bound.pretty(ns, false) + "}".text()
            else -> "($x : ".text() + bound.pretty(ns, false) + ")".text()
        }
        var nsLocal = ns + x
        var rest = body
        while (rest is TPi) {
            x = nsLocal.fresh(rest.name)
            b += when {
                x == "_" -> " → ".text() + rest.bound.pretty(nsLocal, p)
                rest.icit == Icit.Expl -> " ($x : ".text() + rest.bound.pretty(nsLocal, p) + ")".text()
                else -> " {$x : ".text() + rest.bound.pretty(nsLocal, p) + "}".text()
            }
            nsLocal += x
            rest = rest.body
        }
        par(p, b + " → ".text() + rest.pretty(nsLocal, p))
    }
}
