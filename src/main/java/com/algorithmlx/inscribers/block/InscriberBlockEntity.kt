package com.algorithmlx.inscribers.block

import com.algorithmlx.inscribers.network.InscribersNetwork
import com.algorithmlx.inscribers.api.block.ContainerBlockEntity
import com.algorithmlx.inscribers.api.handler.SidedItemHandlerModifiable
import com.algorithmlx.inscribers.api.handler.StackHandler
import com.algorithmlx.inscribers.api.menu
import com.algorithmlx.inscribers.energy.InscribersEnergyStorage
import com.algorithmlx.inscribers.init.config.InscribersConfig
import com.algorithmlx.inscribers.init.registry.InscribersRecipeTypes
import com.algorithmlx.inscribers.init.registry.Register
import com.algorithmlx.inscribers.network.packet.SDirectionPack
import com.algorithmlx.inscribers.recipe.InscriberRecipe
import com.algorithmlx.inscribers.server.InscriberDirectionSettingsServer
import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.container.Container
import net.minecraft.inventory.container.INamedContainerProvider
import net.minecraft.nbt.CompoundNBT
import net.minecraft.tileentity.ITickableTileEntity
import net.minecraft.util.Direction
import net.minecraft.util.Direction.*
import net.minecraft.util.text.ITextComponent
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.energy.CapabilityEnergy
import net.minecraftforge.items.CapabilityItemHandler
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

class InscriberBlockEntity(): ContainerBlockEntity(Register.inscriberBlockEntity.get()), ITickableTileEntity, INamedContainerProvider {
    val energy: InscribersEnergyStorage = InscribersEnergyStorage(InscribersConfig.inscriberCapacity.get()) {}
    private val inventory = StackHandler(36, this::change)
    private var isWorking = false

    private val energyLazy = LazyOptional.of(this::energy)
    private val inventoryCap = SidedItemHandlerModifiable.create(
        this.inventory,
        arrayOf(DOWN, UP, NORTH, SOUTH, WEST, EAST),
        null,
        this::canExtractBySlot
    )

    @TestOnly
    @ApiStatus.Experimental
    @Deprecated("Deprecated as not valid work", level = DeprecationLevel.HIDDEN)
    private val exitsSides: IntArray = intArrayOf(0)

    private var progress: Int = 0
    private var recipe: InscriberRecipe? = null

    override fun getInv(): StackHandler = this.inventory

    override fun tick() {
        val level = this.getLevel()
        if (level == null || level.isClientSide) return

        if (this.recipe == null || !this.recipe!!.matches(inventory)) {
            val locRecipe = level.recipeManager.getRecipeFor(InscribersRecipeTypes.inscriberRecipe, this.inventory.toContainer(), level)
                .orElse(null)
            if (locRecipe is InscriberRecipe) {
                this.recipe = locRecipe
            }
        }

        if (this.recipe != null) {
            val needsEnergy = this.recipe!!.energyPerTick // Needs energy per tick
            val resultTime = this.recipe!!.time
            this.isWorking = true
            if (this.energy.energyStored >= needsEnergy) {
                this.progress += 1
                this.energy.extractEnergy(needsEnergy, simulate = false)

                if (this.progress >= resultTime) {
                    var i = 1
                    val finalSlot = 36
                    while (i < finalSlot) {
                        this.inventory.extract(i, 1, simulate = false)
                        i += 1
                    }
                    this.inventory.setStackInSlot(0, this.recipe!!.result(this.inventory))
                    this.progress = 0
                    this.isWorking = false
                    this.change()
                }
            }
        }
    }

    override fun save(tag: CompoundNBT): CompoundNBT {
        super.save(tag)
        tag.putInt("InscriberProgress", this.progress)
        tag.putInt("InscriberEnergy", this.energy.energyStored)
        tag.putInt("InscriberExitSide", InscriberDirectionSettingsServer.data)
        tag.putBoolean("EnableInscriberExitSide", InscriberDirectionSettingsServer.enabled)
        return tag
    }

    override fun load(state: BlockState, tag: CompoundNBT) {
        super.load(state, tag)
        this.progress = tag.getInt("InscriberProgress")
        this.energy.setStored(tag.getInt("InscriberEnergy"))
        InscribersNetwork.sendToServer(SDirectionPack(tag.getInt("InscriberExitSide"), tag.getBoolean("EnableInscriberExitSide")))
    }

    override fun getDisplayName(): ITextComponent = menu("inscriber")

    override fun createMenu(windowId : Int, inventory : PlayerInventory, player : PlayerEntity): Container? = null

    override fun <T> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (cap == CapabilityEnergy.ENERGY) return energyLazy.cast()

        if (side != null && cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            val sideData = InscriberDirectionSettingsServer.data

            if (sideData == 0 && side == DOWN) return this.inventoryCap[sideData]!!.cast()
            else if (sideData == 1 && side == UP) return this.inventoryCap[sideData]!!.cast()
            else if (sideData == 2 && side == NORTH) return this.inventoryCap[sideData]!!.cast()
            else if (sideData == 3 && side == SOUTH) return this.inventoryCap[sideData]!!.cast()
            else if (sideData == 4 && side == WEST) return this.inventoryCap[sideData]!!.cast()
            else if (sideData == 5 && side == EAST) return this.inventoryCap[sideData]!!.cast()
        }

        return super.getCapability(cap, side)
    }

    /*
    -------------------------------------------------------------------------
    | 0 -> Down | 1 -> Up | 2 -> North | 3 -> South | 4 -> West | 5 -> East |
    _________________________________________________________________________
    */
    private fun canExtractBySlot(slot: Int, direction: Direction?): Boolean {
        if (direction == null)
            return true
        if (slot == 0) {
            val side = InscriberDirectionSettingsServer.data
            val enabled = InscriberDirectionSettingsServer.enabled

            if (side == 0 && direction == DOWN) return enabled
            else if (side == 1 && direction == UP) return enabled
            else if (side == 2 && direction == NORTH) return enabled
            else if (side == 3 && direction == SOUTH) return enabled
            else if (side == 4 && direction == WEST) return enabled
            else if (side == 5 && direction == EAST) return enabled
        }
        return false
    }

    fun getProgress(): Int = this.progress

    fun getTime(): Int = if (this.recipe == null) 0 else this.recipe!!.time
}