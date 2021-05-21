package simple

import pretty.*

enum class Prec {
    Atom,
    App,
    Pi,
    Let
}

fun <A> par(l: Prec, r: Prec, x: Doc<A>): Doc<A> =
    if (l < r) "(".text() + x + ")".text() else x

fun Term.pretty(ns: NameEnv?, p: Prec = Prec.Atom): Doc<Nothing> = when (this) {
    is TVar -> ns[ix].text()
    is TNat -> n.toString().text()
    is TApp -> par(p, Prec.App, arg.pretty(ns, Prec.App) + " ".text() + body.pretty(ns, Prec.Atom))
    is TLam -> {
        var x = ns.fresh(arg)
        var nsLocal = ns + x
        var rest = body
        var b = "λ $x".text()
        while (rest is TLam) {
            x = nsLocal.fresh(rest.arg)
            nsLocal += x
            rest = rest.body
            b += " $x".text()
        }
        par(p, Prec.Let, b + ". ".text() + rest.pretty(nsLocal, Prec.Let))
    }
    is TU -> "*".text()
    is TPi -> when (arg) {
        "_" -> par(p, Prec.Pi, ty.pretty(ns, Prec.App) spaced " ".text() + body.pretty(ns + "_", Prec.Pi))
        else -> {
            var x = ns.fresh(arg)
            var b = "($x : ".text() + ty.pretty(ns, Prec.Let) + ")".text()
            var nsLocal = ns + x
            var rest = body
            while (rest is TPi) {
                x = nsLocal.fresh(rest.arg)
                b += if (x == "_") " → ".text() + rest.ty.pretty(nsLocal, Prec.App)
                else " ($x : ".text() + rest.ty.pretty(nsLocal, Prec.Let) + ")".text()
                nsLocal += x
                rest = rest.body
            }
            par(p, Prec.Pi, b + " → ".text() + rest.pretty(nsLocal, Prec.Pi))
        }
    }
    is TLet -> {
        val d = listOf(
            ":".text() spaced ty.pretty(ns, Prec.Let),
            "=".text() spaced bind.pretty(ns, Prec.Let),
        ).vCat().align()
        val r = listOf(
            "let $n".text() spaced d,
            "in".text() spaced body.pretty(ns + n, Prec.Let)
        ).vCat().align()
        par(p, Prec.Let, r)
    }
}