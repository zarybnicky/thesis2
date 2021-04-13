package montuno.interpreter.meta

import montuno.interpreter.Icit
import montuno.interpreter.Ix
import montuno.interpreter.Lvl

fun VEnv.local(ix: Ix): Lazy<Val> = it[ix.it] ?: lazyOf(vLocal(ix))
fun GEnv.local(ix: Ix): Glued = it[ix.it] ?: gLocal(ix)

fun TopContext.top(lvl: Lvl): Glued = when (val top = topEntries[lvl.it].defn) {
    null -> gTop(lvl)
    else -> top.gv.g
}

fun Term.evalBox(top: TopContext, env: VEnv): Lazy<Val> = when (this) {
    is TLocal -> env.local(ix)
    is TTop -> lazyOf(vTop(lvl))
    is TMeta -> lazyOf(vMeta(meta))
    else -> lazy { eval(top, env) }
}
fun Term.eval(top: TopContext, env: VEnv): Val = when (this) {
    is TLocal -> env.local(ix).value
    is TTop -> vTop(lvl)
    is TMeta -> when (val m = top[meta]) {
        is MetaSolved -> m.gv.v.value
        else -> vMeta(meta)
    }
    is TApp -> l.eval(top, env).app(top, icit, r.evalBox(top, env))
    is TLam -> VLam(n, icit, VCl(env, tm))
    is TPi -> VPi(n, icit, arg.evalBox(top, env), VCl(env, tm))
    is TFun -> VFun(l.evalBox(top, env), r.evalBox(top, env))
    is TLet -> tm.eval(top, env.def(lazy { v.eval(top, env) }))
    is TU -> VU
    is TIrrelevant -> VIrrelevant
    is TForeign -> TODO("VForeign not implemented")
}
fun Term.gEvalBox(top: TopContext, env: VEnv, genv: GEnv): Glued = when (this) {
    is TLocal -> genv.local(ix)
    is TTop -> top.top(lvl)
    is TMeta -> when (val m = top[meta]) {
        is MetaSolved -> m.gv.g
        else -> gMeta(meta)
    }
    else -> gEval(top, env, genv) // lazy
}
fun Term.gEval(top: TopContext, env: VEnv, genv: GEnv): Glued = when (this) {
    is TLocal -> genv.local(ix)
    is TTop -> top.top(lvl)
    is TMeta -> when (val m = top[meta]) {
        is MetaSolved -> m.gv.g
        else -> gMeta(meta)
    }
    is TApp -> l.gEval(top, env, genv).app(top, icit, GluedVal(lazy { r.eval(top, env) }, r.gEvalBox(top, env, genv)))
    is TLam -> GLam(n, icit, GCl(genv, env, tm))
    is TPi -> GPi(n, icit, GluedVal(lazy { arg.eval(top, env) }, arg.gEvalBox(top, env, genv)), GCl(genv, env, tm))
    is TFun -> GFun(GluedVal(lazy { l.eval(top, env) }, l.gEvalBox(top, env, genv)), GluedVal(lazy { r.eval(top, env) }, r.gEvalBox(top, env, genv)))
    is TLet -> tm.gEval(top, env.def(lazy { v.eval(top, env) }), genv.def(v.gEvalBox(top, env, genv)))
    is TU -> GU
    is TIrrelevant -> GIrrelevant
    is TForeign -> TODO("GForeign not implemented")
}

fun Term.gvEval(top: TopContext, env: VEnv, genv: GEnv): GluedVal = GluedVal(lazy { eval(top, env) }, gEval(top, env, genv))

fun GCl.inst(top: TopContext, v: GluedVal): Glued = tm.gEval(top, env.def(v.v), genv.def(v.g))
fun VCl.inst(top: TopContext, v: Lazy<Val>): Val = tm.eval(top, env.def(v))
fun GCl.gvInst(top: TopContext, gv: GluedVal) : GluedVal {
    val venv = env.def(gv.v)
    return GluedVal(lazy { tm.eval(top, venv) }, tm.gEval(top, venv, genv.def(gv.g)))
}

fun Glued.gvApp(top: TopContext, icit: Icit, r: GluedVal): GluedVal = when (this) {
    is GLam -> cl.gvInst(top, r)
    is GNe -> {
        val vsp = spine.with(icit to r.v)
        GluedVal(lazyOf(VNe(head, vsp)), GNe(head, gspine.with(icit to r.g), vsp))
    }
    else -> TODO("impossible")
}

fun Glued.app(top: TopContext, icit: Icit, r: GluedVal): Glued = when (this) {
    is GLam -> cl.inst(top, r)
    is GNe -> GNe(head, gspine.with(icit to r.g), spine.with(icit to r.v))
    else -> TODO("impossible")
}

fun Val.quote(top: TopContext, lvl: Lvl, metaless: Boolean = false): Term = when (val v = this.force(top)) {
    is VNe -> {
        var x = when (v.head) {
            is HMeta -> {
                val meta = if (metaless) top[v.head.meta] else null
                when {
                    metaless && meta is MetaSolved && meta.unfoldable -> meta.gv.v.value.appSpine(top, v.spine).quote(top, lvl)
                    else -> TMeta(v.head.meta)
                }
            }
            is HLocal -> TLocal(Ix(lvl.it - v.head.ix.it - 1))
            is HTop -> TTop(v.head.lvl)
        }
        for ((icit, t) in v.spine.it.reversedArray()) {
            x = TApp(icit, x, t.value.quote(top, lvl))
        }
        x
    }
    is VLam -> TLam(v.n, v.icit, v.cl.inst(top, lazyOf(vLocal(Ix(lvl.it)))).quote(top, lvl + 1))
    is VPi -> TPi(v.n, v.icit, v.ty.value.quote(top, lvl), v.cl.inst(top, lazyOf(vLocal(Ix(lvl.it)))).quote(top, lvl + 1))
    is VFun -> TFun(v.a.value.quote(top, lvl), v.b.value.quote(top, lvl))
    is VU -> TU
    is VIrrelevant -> TIrrelevant
}
fun Glued.quote(top: TopContext, lvl: Lvl): Term = when (this) {
    is GNe -> {
        var x = when (head) {
            is HMeta -> TMeta(head.meta)
            is HLocal -> TLocal(Ix(lvl.it - head.ix.it - 1))
            is HTop -> TTop(head.lvl)
        }
        for ((icit, t) in gspine.it.reversedArray()) {
            x = TApp(icit, x, t.quote(top, lvl))
        }
        x
    }
    is GLam -> TLam(n, icit, cl.inst(top, gvLocal(Ix(lvl.it))).quote(top, lvl + 1))
    is GPi -> TPi(n, icit, ty.g.quote(top, lvl), cl.inst(top, gvLocal(Ix(lvl.it))).quote(top, lvl + 1))
    is GFun -> TFun(a.g.quote(top, lvl), b.g.quote(top, lvl))
    is GU -> TU
    is GIrrelevant -> TIrrelevant
}

fun Val.app(top: TopContext, icit: Icit, r: Lazy<Val>) = when (this) {
    is VLam -> cl.inst(top, r)
    is VNe -> VNe(head, spine.with(icit to r))
    else -> TODO("impossible")
}
fun Val.appSpine(top: TopContext, sp: VSpine): Val {
    var x = this
    sp.it.forEach {
        x = x.app(top, it.first, it.second)
    }
    return x
}
fun Val.force(top: TopContext): Val = when {
    this is VNe && head is HMeta -> {
        val m = top[head.meta]
        when {
            m is MetaSolved && m.unfoldable -> m.gv.v.value.appSpine(top, spine).force(top)
            else -> this
        }
    }
    else -> this
}
fun Glued.appSpine(top: TopContext, gsp: GSpine, vsp: VSpine): Glued {
    var x = this
    for (i in gsp.it.indices) {
        x = x.app(top, gsp.it[i].first, GluedVal(vsp.it[i].second, gsp.it[i].second))
    }
    return x
}
fun Glued.force(top: TopContext): Glued = when {
    this is GNe && head is HMeta -> {
        val m = top[head.meta]
        when {
            m is MetaSolved -> m.gv.g.appSpine(top, gspine, spine).force(top)
            else -> this
        }
    }
    else -> this
}