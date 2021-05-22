package montuno.interpreter.scope

import montuno.ElabError
import montuno.Meta
import montuno.interpreter.*
import montuno.syntax.Icit

data class MetaContext(val ctx: MontunoContext) {
    val it: MutableList<MutableList<MetaEntry>> = mutableListOf()

    operator fun get(meta: Meta) = it[meta.i][meta.j]
    operator fun set(meta: Meta, m: MetaEntry) {
        val (i, j) = meta
        if (it[i].size == j) it[i].add(m) else it[i][j] = m
    }

    fun newMetaBlock() = it.add(mutableListOf())
    fun freshType(): Term {
        val meta = (it.size - 1).let { i -> Meta(i, it[i].size) }
        this[meta] = MetaEntry(ctx.loc, VUnit)
        return TMeta(meta, this[meta])
    }
    private fun closeType(env: LocalEnv, a: Term): Term {
        var x = a
        for (i in env.vals.it.indices) {
            x = if (env.vals.it[i] == null) TPi(env.names[i], Icit.Expl, env.types[i].quote(env.lvl), x)
            else TLet(env.names[i]!!, env.types[i].quote(env.lvl), env.vals.it[i]!!.quote(env.lvl), x)
        }
        return x
    }
    fun freshTerm(env: LocalEnv, a: Term): Term {
        val meta = (it.size - 1).let { i -> Meta(i, it[i].size) }
        val type: Val = closeType(env, a).eval(ctx, env.vals)
        this[meta] = MetaEntry(ctx.loc, type)
        return TMeta(meta, this[meta])
    }
    fun simplifyMetaBlock() {
        if (it.size == 0) return
        val blockIx = it.size - 1
        val block = it[blockIx]
        val occurs = IntArray(block.size) { 0 }

        // 1. Inline already inlinable metas in block, check for unsolved metas
        // 2. Mark metas which don't occur in other metas as inlinable
        val ctx = ctx.makeLocalContext()
        for ((ix, meta) in block.withIndex()) when {
            !meta.solved -> throw ElabError(meta.loc, "Unsolved meta ${Meta(blockIx, ix)}")
            else -> {
                meta.term = ctx.inline(meta.term!!)
                meta.value = ctx.eval(meta.term!!)
                meta.unfoldable = meta.term!!.isUnfoldable()
                ctx.markOccurs(occurs, blockIx, meta.term!!)
            }
        }
        for ((ix, meta) in block.withIndex()) {
            if (!meta.solved) throw ElabError(meta.loc, "Unsolved meta ${Meta(blockIx, ix)}")
            if (!meta.unfoldable && occurs[ix] == 0) {
                block[ix].unfoldable = true
            }
        }
    }
}