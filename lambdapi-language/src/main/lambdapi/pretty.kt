package lambdapi

import java.lang.StringBuilder

enum class Prec {
    Atom,
    App,
    Pi,
    Let
}

fun par(l: Prec, r: Prec, x: StringBuilder): StringBuilder =
    if (l < r) StringBuilder("(").append(x).append(")") else x

fun Term.pretty(ns: Names?, p: Prec = Prec.Atom): StringBuilder = when (this) {
    is TVar -> StringBuilder(ns.ix(ix))
    is TLitNat -> StringBuilder(n.toString())
    is TApp -> par(p, Prec.App, arg.pretty(ns, Prec.App).append(" ").append(body.pretty(ns, Prec.Atom)))
    is TLam -> {
        var x = ns.fresh(arg)
        var nsLocal = ns.cons(x)
        var rest = body
        val b = StringBuilder("λ $x")
        while (rest is TLam) {
            x = nsLocal.fresh(rest.arg)
            nsLocal = nsLocal.cons(x)
            rest = rest.body
            b.append(" $x")
        }
        par(p, Prec.Let, b.append(". ").append(rest.pretty(nsLocal, Prec.Let)))
    }
    is TStar -> StringBuilder("*")
    is TNat -> StringBuilder("Nat")
    is TPi -> when (arg) {
        "_" -> par(p, Prec.Pi, ty.pretty(ns, Prec.App).append(" → ").append(body.pretty(ns.cons("_"), Prec.Pi)))
        else -> {
            var x = ns.fresh(arg)
            val b = StringBuilder("($x : ").append(ty.pretty(ns, Prec.Let)).append(")")
            var nsLocal = ns.cons(x)
            var rest = body
            while (rest is TPi) {
                x = nsLocal.fresh(rest.arg)
                if (x == "_") b.append(" → ").append(rest.ty.pretty(nsLocal, Prec.App))
                else b.append(" ($x : ").append(rest.ty.pretty(nsLocal, Prec.Let)).append(")")
                nsLocal = nsLocal.cons(x)
                rest = rest.body
            }
            par(p, Prec.Pi, b.append(" → ").append(rest.pretty(nsLocal, Prec.Pi)))
        }
    }
    is TLet -> par(p, Prec.Let, StringBuilder("let $n : ")
        .append(ty.pretty(ns, Prec.Let))
        .append("\n    = ")
        .append(bind.pretty(ns, Prec.Let))
        .append("\nin\n")
        .append(body.pretty(ns.cons(n), Prec.Let)))
}