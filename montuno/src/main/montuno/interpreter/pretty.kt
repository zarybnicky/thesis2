package montuno.interpreter

import montuno.interpreter.scope.NameEnv
import montuno.syntax.Icit
import pretty.*
import pretty.symbols.comma

fun <A> par(c: Boolean, x: Doc<A>): Doc<A> =
    if (c) "(".text() + x + ")".text() else x

fun Term.pretty(ns: NameEnv, p: Boolean = false): Doc<Nothing> = when (this) {
    is TMeta -> "?${meta.i}.${meta.j}".text()
    is TTop -> slot.name.text()
    is TLocal -> ns[ix].text()
    is TUnit -> "Unit".text()
    is TNat -> n.toString().text()
    is TBool -> n.toString().text()
//    is TForeign -> "[$lang|$code|".text() + type.pretty(ns, false) + "]".text()
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
            x == "_" -> bound.pretty(ns, true)
            icit == Icit.Impl -> "{$x : ".text() + bound.pretty(ns, false) + "}".text()
            else -> "($x : ".text() + bound.pretty(ns, false) + ")".text()
        }
        var nsLocal = ns + x
        var rest = body
        while (rest is TPi) {
            x = nsLocal.fresh(rest.name)
            b += when {
                x == "_" -> " → ".text() + rest.bound.pretty(nsLocal, true)
                rest.icit == Icit.Expl -> " ($x : ".text() + rest.bound.pretty(nsLocal, false) + ")".text()
                else -> " {$x : ".text() + rest.bound.pretty(nsLocal, false) + "}".text()
            }
            nsLocal += x
            rest = rest.body
        }
        par(p, b + " → ".text() + rest.pretty(nsLocal, p))
    }
    is TPair -> {
        val items = mutableListOf<Doc<Nothing>>()
        var x = this
        while (x is TPair) {
            items.add(x.lhs.pretty(ns, false))
            x = x.rhs
        }
        items.add(x.pretty(ns, false))
        par(p, items.punctuate(comma()).hSep())
    }
    is TProj1 -> body.pretty(ns, true) + ".1".text()
    is TProj2 -> body.pretty(ns, true) + ".2".text()
    is TProjF -> body.pretty(ns, true) + ".$name".text()
    is TSg -> {
        val arg = bound.pretty(ns, false)
        val l = if (name != null) "($name : ".text() + arg + ")".text() else arg
        par(p, l + " × ".text() + body.pretty(ns, true))
    }

}
