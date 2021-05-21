package montuno.interpreter.scope

import montuno.ElabError
import montuno.Icit
import montuno.Lvl
import montuno.Meta
import montuno.interpreter.*

data class MetaContext(val ctx: MontunoContext) {
    val it: MutableList<MutableList<MetaEntry>> = mutableListOf()

    operator fun get(meta: Meta) = it[meta.i][meta.j]
    operator fun set(meta: Meta, m: MetaEntry) {
        val (i, j) = meta
        if (it[i].size == j) it[i].add(m) else it[i][j] = m
    }

    fun newMetaBlock() = it.add(mutableListOf())
    fun newMeta(depth: Lvl, boundLevels: IntArray): Term {
        val i = it.size - 1
        val meta = Meta(i, it[i].size)
        this[meta] = MetaEntry(ctx.loc)
        var ret: Term = TMeta(meta, this[meta])
        for (l in boundLevels) {
            ret = TApp(Icit.Expl, ret, TLocal(Lvl(l).toIx(depth)))
        }
        return ret
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