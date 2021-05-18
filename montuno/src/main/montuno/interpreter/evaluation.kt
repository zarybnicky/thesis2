package montuno.interpreter

fun Term.evalBox(env: VEnv): Lazy<Val> = when (this) {
    is TLocal -> env.local(ix.toLvl(env.lvl))
    is TTop -> lazyOf(vTop(lvl))
    is TMeta -> lazyOf(vMeta(meta))
    else -> lazy { eval(env) }
}

fun Term.eval(env: VEnv): Val = when (this) {
    is TLocal -> env.local(ix.toLvl(env.lvl)).value
    is TTop -> vTop(lvl)
    is TMeta -> MontunoPure.top.getMetaForce(meta)
    is TApp -> l.eval(env).app(icit, r.evalBox(env))
    is TLam -> VLam(n, icit, VCl(env, tm))
    is TPi -> VPi(n, icit, arg.evalBox(env), VCl(env, tm))
    is TFun -> VFun(l.evalBox(env), r.evalBox(env))
    is TLet -> tm.eval(env.def(lazy { v.eval(env) }))
    is TU -> VU
    is TNat -> VNat(n)
    is TIrrelevant -> VIrrelevant
    is TForeign -> TODO("VForeign not implemented")
}

fun Term.gEvalBox(env: VEnv, genv: GEnv): Glued = when (this) {
    is TLocal -> genv.local(ix.toLvl(env.lvl))
    is TTop -> MontunoPure.top.getTopGlued(lvl)
    is TMeta -> MontunoPure.top.getMetaGlued(meta)
    else -> gEval(env, genv) // lazy
}

fun Term.gEval(env: VEnv, genv: GEnv): Glued = when (this) {
    is TLocal -> genv.local(ix.toLvl(env.lvl))
    is TTop -> MontunoPure.top.getTopGlued(lvl)
    is TMeta -> MontunoPure.top.getMetaGlued(meta)
    is TApp -> l.gEval(env, genv).app(icit, GluedVal(lazy { r.eval(env) }, r.gEvalBox(env, genv)))
    is TLam -> GLam(n, icit, GCl(genv, env, tm))
    is TPi -> GPi(n, icit, GluedVal(lazy { arg.eval(env) }, arg.gEvalBox(env, genv)), GCl(genv, env, tm))
    is TFun -> GFun(GluedVal(lazy { l.eval(env) }, l.gEvalBox(env, genv)), GluedVal(lazy { r.eval(env) }, r.gEvalBox(env, genv)))
    is TLet -> tm.gEval(env.def(lazy { v.eval(env) }), genv.def(v.gEvalBox(env, genv)))
    is TU -> GU
    is TNat -> GNat(n)
    is TIrrelevant -> GIrrelevant
    is TForeign -> TODO("GForeign not implemented")
}

fun Term.gvEval(env: VEnv, genv: GEnv): GluedVal = GluedVal(lazy { eval(env) }, gEval(env, genv))

fun GCl.inst(v: GluedVal): Glued = tm.gEval(env.def(v.v), genv.def(v.g))
fun VCl.inst(v: Lazy<Val>): Val = tm.eval(env.def(v))
fun GCl.gvInst(gv: GluedVal) : GluedVal {
    val venv = env.def(gv.v)
    return GluedVal(lazy { tm.eval(venv) }, tm.gEval(venv, genv.def(gv.g)))
}

fun Glued.gvApp(icit: Icit, r: GluedVal): GluedVal = when (this) {
    is GLam -> cl.gvInst(r)
    is GNe -> {
        val vsp = spine.with(icit to r.v)
        GluedVal(lazyOf(VNe(head, vsp)), GNe(head, gspine.with(icit to r.g), vsp))
    }
    else -> TODO("impossible")
}

fun Glued.app(icit: Icit, r: GluedVal): Glued = when (this) {
    is GLam -> cl.inst(r)
    is GNe -> GNe(head, gspine.with(icit to r.g), spine.with(icit to r.v))
    else -> TODO("impossible $this")
}

fun Val.quote(lvl: Lvl, metaless: Boolean = false): Term = when (val v = this.force()) {
    is VNe -> {
        var x = when (v.head) {
            is HMeta -> {
                val meta = if (metaless) MontunoPure.top[v.head.meta] else null
                when {
                    metaless && meta is MetaSolved && meta.unfoldable -> meta.gv.v.value.appSpine(v.spine).quote(lvl)
                    else -> TMeta(v.head.meta)
                }
            }
            is HLocal -> TLocal(v.head.lvl.toIx(lvl))
            is HTop -> TTop(v.head.lvl)
        }
        for ((icit, t) in v.spine.it.reversedArray()) {
            x = TApp(icit, x, t.value.quote(lvl))
        }
        x
    }
    is VLam -> TLam(v.n, v.icit, v.cl.inst(lazyOf(vLocal(lvl))).quote(lvl + 1))
    is VPi -> TPi(v.n, v.icit, v.ty.value.quote(lvl), v.cl.inst(lazyOf(vLocal(lvl))).quote(lvl + 1))
    is VFun -> TFun(v.a.value.quote(lvl), v.b.value.quote(lvl))
    is VU -> TU
    is VNat -> TNat(v.n)
    is VIrrelevant -> TIrrelevant
}
fun Glued.quote(lvl: Lvl): Term = when (this) {
    is GNe -> {
        var x = when (head) {
            is HMeta -> TMeta(head.meta)
            is HLocal -> TLocal(head.lvl.toIx(lvl))
            is HTop -> TTop(head.lvl)
        }
        for ((icit, t) in gspine.it.reversedArray()) {
            x = TApp(icit, x, t.quote(lvl))
        }
        x
    }
    is GLam -> TLam(n, icit, cl.inst(gvLocal(lvl)).quote(lvl + 1))
    is GPi -> TPi(n, icit, ty.g.quote(lvl), cl.inst(gvLocal(lvl)).quote(lvl + 1))
    is GFun -> TFun(a.g.quote(lvl), b.g.quote(lvl))
    is GU -> TU
    is GNat -> TNat(n)
    is GIrrelevant -> TIrrelevant
}

fun Val.app(icit: Icit, r: Lazy<Val>) = when (this) {
    is VLam -> cl.inst(r)
    is VNe -> VNe(head, spine.with(icit to r))
    else -> TODO("impossible")
}
fun Val.appSpine(sp: VSpine): Val {
    var x = this
    sp.it.forEach {
        x = x.app(it.first, it.second)
    }
    return x
}
fun Val.force(): Val = when {
    this is VNe && head is HMeta -> {
        val m = MontunoPure.top[head.meta]
        when {
            m is MetaSolved && m.unfoldable -> m.gv.v.value.appSpine(spine).force()
            else -> this
        }
    }
    else -> this
}
fun Glued.appSpine(gsp: GSpine, vsp: VSpine): Glued {
    var x = this
    for (i in gsp.it.indices) {
        x = x.app(gsp.it[i].first, GluedVal(vsp.it[i].second, gsp.it[i].second))
    }
    return x
}
fun Glued.force(): Glued = when {
    this is GNe && head is HMeta -> when (val m = MontunoPure.top[head.meta]) {
        is MetaSolved -> m.gv.g.appSpine(gspine, spine).force()
        else -> this
    }
    else -> this
}
