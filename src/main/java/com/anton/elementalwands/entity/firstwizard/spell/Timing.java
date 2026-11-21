package com.anton.elementalwands.entity.firstwizard.spell;

/**
 * When the parts of a cast happen, in ticks from its start. A spell forms in the open from {@code formFrom} until its
 * {@code release}; its aim (or spot) is fixed at {@code aimLock}; at {@code release} it is thrown or takes effect; at
 * {@code free} the copy may move and choose again. Whatever the spell leaves behind may run on after that.
 *
 * <p>The contract check holds every implemented spell to formFrom &lt; aimLock &lt;= release &lt; free, and to a warning
 * of at least {@link #MIN_WARNING} ticks of forming before it is released.</p>
 */
public record Timing(int formFrom, int aimLock, int release, int free) {
    /** Half a second: the least a spell may form in the open before it is released. */
    public static final int MIN_WARNING = 10;

    public boolean consistent() { return formFrom >= 0 && formFrom < aimLock && aimLock <= release && release < free; }
    /** Ticks it is seen forming before it is released. */
    public int warning() { return release - formFrom; }
}
