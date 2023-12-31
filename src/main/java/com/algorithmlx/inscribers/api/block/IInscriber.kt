package com.algorithmlx.inscribers.api.block

import com.algorithmlx.inscribers.api.translate
import com.algorithmlx.inscribers.init.config.InscribersConfig
import com.algorithmlx.inscribers.init.registry.Register
import net.minecraft.block.Block
import net.minecraft.state.DirectionProperty
import net.minecraft.util.Direction
import net.minecraft.util.text.TextFormatting
import java.util.function.Predicate
import kotlin.math.sqrt

interface IInscriber {
    /**
     * Sets container size.
     *
     * Default: 2
     * @return [Int]
     */
    fun getSize(): Int = 2
    /**
     * Sets Inscriber Type
     * @return [InscriberType]
     */
    fun getType(): InscriberType

    /**
     * Sets energy capacity
     * @return [Int]
     */
    fun getEnergy(): Int = InscribersConfig.inscriberCapacity.get()

    private val sqrtSize: Int
        get() = sqrt(this.getSize().toFloat()).toInt()

    fun getXSize(): Int = this.sqrtSize

    fun getYSize(): Int = this.sqrtSize

    fun getTier(): InscriberTier

    companion object {
        @get:JvmStatic
        val boundedInscribers: MutableList<IInscriber> = mutableListOf()
    }

    enum class InscriberType {
        STANDARD_INSCRIBER;

        fun getTranslationName() = translate("api.type", this.name.lowercase())
    }

    enum class InscriberTier(
        @get:JvmName("getEnergyMultiplier") val energyCostMultiplier: Double,
        val timeBoost: Double,
        val withFormatting: TextFormatting = TextFormatting.WHITE
    ) {
        BASIC(1.0, -5.0),
        IMPROVED(3.0, -3.0, TextFormatting.DARK_GRAY),
        ADVANCED(5.0, 1.0, TextFormatting.GOLD),
        ELITE(10.0, 5.0, TextFormatting.DARK_AQUA),
        PERFECT(16.5, 10.0, TextFormatting.GREEN),
        MAXIMIZED(53.8, 30.0, TextFormatting.LIGHT_PURPLE);

        fun getTranslationName() = translate("tier", this.name.lowercase()).withStyle(this.withFormatting)
    }

    object InscriberStates {
        val standardVariant = horizontal("inscriber_classic")
        var rotated = direction("inscriber_rot", Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN)

        fun horizontal(id: String): DirectionProperty = direction(id, Direction.Plane.HORIZONTAL)

        fun direction(id: String, vararg direction: Direction): DirectionProperty = DirectionProperty.create(
            id,
            *direction
        )

        fun direction(id: String, filter: Predicate<Direction>): DirectionProperty = DirectionProperty.create(
            id,
            filter
        )

        fun direction(id: String, values: Collection<Direction>): DirectionProperty = DirectionProperty.create(id, values)
    }
}