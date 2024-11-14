package gregtech.common.tileentities.machines.multi;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.IAlignment;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.IAlignmentProvider;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.ProgressBar;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.GTMod;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ParticleFX;
import gregtech.api.enums.SteamVariant;
import gregtech.api.gui.modularui.GTUIInfos;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.gui.modularui.GUITextureSet;
import gregtech.api.interfaces.modularui.IAddUIWidgets;
import gregtech.api.interfaces.modularui.IGetTitleColor;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.RecipeMapWorkable;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.objects.GTItemStack;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.WorldSpawnedEventBuilder.ParticleEventBuilder;
import gregtech.common.pollution.Pollution;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static gregtech.api.objects.XSTR.XSTR_INSTANCE;


public abstract class MTEPrimitiveBlastFurnace extends MetaTileEntity
    implements IAlignment, ISurvivalConstructable, RecipeMapWorkable, IAddUIWidgets, IGetTitleColor {
    private static final String tier1 = "tier1";
    private static final String tier2 = "tier2";
    private static final String tier3 = "tier3";

    private int mSetSlotSizeMultiplier = 1;
    private int mSetTier = -1;
    private int mSetRecipeAcceleration = 1;

    public static final int INPUT_SLOTS = 3, OUTPUT_SLOTS = 3;
    private static final ClassValue<IStructureDefinition<MTEPrimitiveBlastFurnace>> STRUCTURE_DEFINITION = new ClassValue<>() {

        @Override
        protected IStructureDefinition<MTEPrimitiveBlastFurnace> computeValue(Class<?> type) {
            return IStructureDefinition.<MTEPrimitiveBlastFurnace>builder()
                .addShape(
                    tier1,
                    transpose(
                        new String[][] {
                            { " c ", "c-c", " c " },
                            { " c ", "clc", " c " },
                            { "ccc", "clc", "ccc" },
                            { "ccc", "clc", "ccc" },
                            { "c~c", "ccc", "ccc" },
                        }))
                .addShape(
                    tier2,
                    transpose(
                        new String[][] {
                            { "     ", "  c  ", " c-c ", "  c  ", "     " },
                            { "     ", "  c  ", " clc ", "  c  ", "     " },
                            { "     ", "  c  ", " clc ", "  c  ", "     " },
                            { "     ", " ccc ", "cclcc", " ccc ", "     " },
                            { "  c  ", " ccc ", "cclcc", " ccc ", "  c  " },
                            { " c~c ", "ccccc", "ccccc", "ccccc", " ccc " },
                        }))
                .addShape(
                    tier3,
                    transpose(
                        new String[][] {
                            { "         ", "  c      ", " c-c     ", "  c      ", "         " },
                            { "         ", "  c      ", " clc     ", "  c      ", "         " },
                            { "         ", "  c   c  ", " clc c-c ", "  c   c  ", "         " },
                            { "         ", " ccc  c  ", " clc clc ", " ccc  c  ", "         " },
                            { "         ", " ccc ccc ", " clc clc ", " ccc ccc ", "         " },
                            { "  c   c  ", " ccc ccc ", "cclccclcc", " ccc ccc ", "  c   c  " },
                            { " c~c ccc ", "ccccccccc", "ccccccccc", "ccccccccc", " ccc ccc " },
                        }))
                .addElement('c', lazy(t -> ofBlock(t.getCasingBlock(), t.getCasingMetaID())))
                .addElement(
                    'l',
                    ofChain(isAir(), ofBlockAnyMeta(Blocks.lava, 1), ofBlockAnyMeta(Blocks.flowing_lava, 1)))
                .build();
        }
    };

    public int mMaxProgresstime = 0;
    private volatile boolean mUpdated;
    public int mUpdate = 5;
    public int mProgresstime = 0;
    public boolean mMachine = false;

    public ItemStack[] mOutputItems = new ItemStack[OUTPUT_SLOTS];

    public MTEPrimitiveBlastFurnace(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, INPUT_SLOTS + OUTPUT_SLOTS);
    }

    public MTEPrimitiveBlastFurnace(String aName) {
        super(aName, INPUT_SLOTS + OUTPUT_SLOTS);
    }

    @Override
    public boolean isTeleporterCompatible() {
        return false;
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return (facing.flag & (ForgeDirection.UP.flag | ForgeDirection.DOWN.flag)) == 0;
    }

    @Override
    public boolean isAccessAllowed(EntityPlayer aPlayer) {
        return true;
    }

    @Override
    public int getProgresstime() {
        return this.mProgresstime;
    }

    @Override
    public int maxProgresstime() {
        return this.mMaxProgresstime;
    }

    public int getTier() { return this.mSetTier; }

    @Override
    public int increaseProgress(int aProgress) {
        this.mProgresstime += aProgress;
        return this.mMaxProgresstime - this.mProgresstime;
    }

    @Override
    public boolean allowCoverOnSide(ForgeDirection side, GTItemStack aCoverID) {
        return (GregTechAPI.getCoverBehaviorNew(aCoverID.toStack())
            .isSimpleCover()) && (super.allowCoverOnSide(side, aCoverID));
    }

    @Override
    public abstract MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity);

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        aNBT.setInteger("mProgresstime", this.mProgresstime);
        aNBT.setInteger("mMaxProgresstime", this.mMaxProgresstime);
        aNBT.setInteger("mSetTier", mSetTier);
        aNBT.setInteger("mSetSlotSizeMultiplier", mSetSlotSizeMultiplier);
        aNBT.setInteger("mSetRecipeAcceleration", mSetRecipeAcceleration);
        if (this.mOutputItems != null) {
            for (int i = 0; i < mOutputItems.length; i++) {
                if (this.mOutputItems[i] != null) {
                    NBTTagCompound tNBT = new NBTTagCompound();
                    this.mOutputItems[i].writeToNBT(tNBT);
                    aNBT.setTag("mOutputItem" + i, tNBT);
                }
            }
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        this.mUpdate = 5;
        this.mProgresstime = aNBT.getInteger("mProgresstime");
        this.mMaxProgresstime = aNBT.getInteger("mMaxProgresstime");
        this.mSetTier = aNBT.getInteger("mSetTier");
        this.mSetSlotSizeMultiplier = aNBT.getInteger("mSetSlotSizeMultiplier");
        this.mSetRecipeAcceleration = aNBT.getInteger("mSetRecipeAcceleration");
        this.mOutputItems = new ItemStack[OUTPUT_SLOTS];
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            this.mOutputItems[i] = GTUtility.loadItem(aNBT, "mOutputItem" + i);
        }
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        GTUIInfos.openGTTileEntityUI(aBaseMetaTileEntity, aPlayer);
        return true;
    }

    @Override
    public ExtendedFacing getExtendedFacing() {
        return ExtendedFacing.of(getBaseMetaTileEntity().getFrontFacing());
    }

    @Override
    public void setExtendedFacing(ExtendedFacing alignment) {
        getBaseMetaTileEntity().setFrontFacing(alignment.getDirection());
    }

    @Override
    public IAlignmentLimits getAlignmentLimits() {
        return (d, r, f) -> (d.flag & (ForgeDirection.UP.flag | ForgeDirection.DOWN.flag)) == 0 && r.isNotRotated()
            && f.isNotFlipped();
    }

    private boolean checkMachine() {
        boolean structureValid;
        structureValid = STRUCTURE_DEFINITION.get(this.getClass())
            .check(
                this,
                tier3,
                getBaseMetaTileEntity().getWorld(),
                getExtendedFacing(),
                getBaseMetaTileEntity().getXCoord(),
                getBaseMetaTileEntity().getYCoord(),
                getBaseMetaTileEntity().getZCoord(),
                2,
                6,
                0,
                !mMachine);
        if (structureValid) {
            mSetTier = 3;
            mSetRecipeAcceleration = 5;
            mSetSlotSizeMultiplier = 5;

            return true;
        }
        structureValid = STRUCTURE_DEFINITION.get(this.getClass())
            .check(
                this,
                tier2,
                getBaseMetaTileEntity().getWorld(),
                getExtendedFacing(),
                getBaseMetaTileEntity().getXCoord(),
                getBaseMetaTileEntity().getYCoord(),
                getBaseMetaTileEntity().getZCoord(),
                2,
                5,
                0,
                !mMachine);
        if (structureValid) {
            mSetTier = 2;
            mSetSlotSizeMultiplier = 2;
            mSetRecipeAcceleration = 2;
            return true;
        }

        structureValid = STRUCTURE_DEFINITION.get(this.getClass())
            .check(
                this,
                tier1,
                getBaseMetaTileEntity().getWorld(),
                getExtendedFacing(),
                getBaseMetaTileEntity().getXCoord(),
                getBaseMetaTileEntity().getYCoord(),
                getBaseMetaTileEntity().getZCoord(),
                1,
                4,
                0,
                !mMachine);
        if (structureValid) {
            mSetSlotSizeMultiplier = 1;
            mSetRecipeAcceleration = 1;
            mSetTier = 1;
        }
        return structureValid;
    }

    protected abstract Block getCasingBlock();

    protected abstract int getCasingMetaID();

    @Override
    public void onMachineBlockUpdate() {
        mUpdated = true;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTimer) {
        int lavaX;
        int lavaZ;
        int lavaY;
        ForgeDirection facing = aBaseMetaTileEntity.getBackFacing();
        if (mSetTier == -1){
            checkMachine();
        }

        if (mSetTier == 3) {
            lavaX = aBaseMetaTileEntity.getOffsetX(facing, 2);
            lavaY = aBaseMetaTileEntity.getOffsetY(facing, 1) + 6;
            lavaZ = aBaseMetaTileEntity.getOffsetZ(facing, 2);
        } else if (mSetTier == 2) {
            lavaX = aBaseMetaTileEntity.getOffsetX(facing, 2);
            lavaY = aBaseMetaTileEntity.getOffsetY(facing, 1) + 5;
            lavaZ = aBaseMetaTileEntity.getOffsetZ(facing, 2);
        } else {
            lavaX = aBaseMetaTileEntity.getOffsetX(facing, 1);
            lavaY = aBaseMetaTileEntity.getOffsetY(facing, 1) + 4;
            lavaZ = aBaseMetaTileEntity.getOffsetZ(facing, 1);
        }

        if ((aBaseMetaTileEntity.isClientSide()) && (aBaseMetaTileEntity.isActive())) {

            new ParticleEventBuilder().setMotion(0D, 0.3D, 0D)
                .setIdentifier(ParticleFX.LARGE_SMOKE)
                .setPosition(
                    lavaX + XSTR_INSTANCE.nextFloat(),
                    lavaY,
                    lavaZ + XSTR_INSTANCE.nextFloat())
                .setWorld(getBaseMetaTileEntity().getWorld())
                .run();
            if (mSetTier == 3){
                new ParticleEventBuilder().setMotion(0D, 0.3D, 0D)
                    .setIdentifier(ParticleFX.LARGE_SMOKE)
                    .setPosition(
                        lavaX - facing.offsetZ * 4 + XSTR_INSTANCE.nextFloat(),
                        lavaY + XSTR_INSTANCE.nextFloat() - 3,
                        lavaZ + facing.offsetX * 4 + XSTR_INSTANCE.nextFloat())
                    .setWorld(getBaseMetaTileEntity().getWorld())
                    .run();
            }
        }
        if (aBaseMetaTileEntity.isServerSide()) {
            if (mUpdated) {
                // duct tape fix for too many updates on an overloaded server, causing the structure check to not run
                if (mUpdate < 0) mUpdate = 5;
                mUpdated = false;
            }
            if (this.mUpdate-- == 0) {
                this.mMachine = checkMachine();
            }
            if (this.mMachine) {
                if (this.mMaxProgresstime > 0) {
                    if (++this.mProgresstime >= this.mMaxProgresstime) {
                        addOutputProducts();
                        this.mOutputItems = null;
                        this.mProgresstime = 0;
                        this.mMaxProgresstime = 0;
                        GTMod.achievements.issueAchievement(
                            aBaseMetaTileEntity.getWorld()
                                .getPlayerEntityByName(aBaseMetaTileEntity.getOwnerName()),
                            "steel");
                    }
                } else if (aBaseMetaTileEntity.isAllowedToWork()) {
                    checkRecipe();
                }
            }
            if (this.mMaxProgresstime > 0 && (aTimer % 20L == 0L)) {
                Pollution.addPollution(
                    this.getBaseMetaTileEntity(),
                    GTMod.gregtechproxy.mPollutionPrimitveBlastFurnacePerSecond);
            }

            aBaseMetaTileEntity.setActive((this.mMaxProgresstime > 0) && (this.mMachine));
            final short controllerY = aBaseMetaTileEntity.getYCoord();
            if (aBaseMetaTileEntity.isActive()) {
                if (aBaseMetaTileEntity.getAir(lavaX, controllerY + mSetTier + 2, lavaZ)) {
                    aBaseMetaTileEntity.getWorld()
                        .setBlock(lavaX, controllerY + mSetTier + 2, lavaZ, Blocks.lava, 1, 2);
                    this.mUpdate = 1;
                }
                if (mSetTier == 3){
                    if (aBaseMetaTileEntity.getAir(lavaX - facing.offsetZ * 4, controllerY + 2, lavaZ + facing.offsetX * 4)) {
                        aBaseMetaTileEntity.getWorld()
                            .setBlock(lavaX - facing.offsetZ * 4, controllerY + 2, lavaZ + facing.offsetX * 4, Blocks.lava, 1, 2);
                        this.mUpdate = 1;
                    }
                }
            } else {
                Block secondPipe = aBaseMetaTileEntity.getBlock(lavaX - facing.offsetZ * 4, controllerY + 2, lavaZ + facing.offsetX * 4);
                Block upperLava = aBaseMetaTileEntity.getBlock(lavaX, controllerY + mSetTier + 2, lavaZ);
                if (mSetTier == 3 && secondPipe == Blocks.lava){
                    aBaseMetaTileEntity.getWorld()
                        .setBlock(lavaX - facing.offsetZ * 4, controllerY + 2, lavaZ + facing.offsetX * 4, Blocks.air, 0, 2);
                    this.mUpdate = 1;
                }
                if (upperLava == Blocks.lava) {
                    aBaseMetaTileEntity.getWorld()
                        .setBlock(lavaX, controllerY + mSetTier + 2, lavaZ, Blocks.air, 0, 2);
                    this.mUpdate = 1;
                }
            }
        }
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (aBaseMetaTileEntity.isClientSide())
            StructureLibAPI.queryAlignment((IAlignmentProvider) aBaseMetaTileEntity);
    }

    /**
     * Draws random flames and smoke particles in front of Primitive Blast Furnace when active
     *
     * @param aBaseMetaTileEntity The entity that will handle the {@link Block#randomDisplayTick}
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void onRandomDisplayTick(IGregTechTileEntity aBaseMetaTileEntity) {
        if (aBaseMetaTileEntity.isActive()) {

            final ForgeDirection frontFacing = aBaseMetaTileEntity.getFrontFacing();

            final double oX = aBaseMetaTileEntity.getOffsetX(frontFacing, 1) + 0.5D;
            final double oY = aBaseMetaTileEntity.getOffsetY(frontFacing, 1);
            final double oZ = aBaseMetaTileEntity.getOffsetZ(frontFacing, 1) + 0.5D;
            final double offset = -0.48D;
            final double horizontal = XSTR_INSTANCE.nextFloat() * 8D / 16D - 4D / 16D;

            final double x, y, z;

            y = oY + XSTR_INSTANCE.nextFloat() * 10D / 16D + 5D / 16D;

            if (frontFacing == ForgeDirection.WEST) {
                x = oX - offset;
                z = oZ + horizontal;
            } else if (frontFacing == ForgeDirection.EAST) {
                x = oX + offset;
                z = oZ + horizontal;
            } else if (frontFacing == ForgeDirection.NORTH) {
                x = oX + horizontal;
                z = oZ - offset;
            } else // if (frontFacing == ForgeDirection.SOUTH.ordinal())
            {
                x = oX + horizontal;
                z = oZ + offset;
            }

            ParticleEventBuilder particleEventBuilder = (new ParticleEventBuilder()).setMotion(0D, 0D, 0D)
                .setPosition(x, y, z)
                .setWorld(getBaseMetaTileEntity().getWorld());
            particleEventBuilder.setIdentifier(ParticleFX.SMOKE)
                .run();
            particleEventBuilder.setIdentifier(ParticleFX.FLAME)
                .run();
        }
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.primitiveBlastRecipes;
    }

    private void addOutputProducts() {
        if (this.mOutputItems == null) {
            return;
        }
        int limit = Math.min(mOutputItems.length, OUTPUT_SLOTS);
        for (int i = 0; i < limit; i++) {
            int absi = INPUT_SLOTS + i;
            if (this.mInventory[absi] == null) {
                this.mInventory[absi] = GTUtility.copyOrNull(this.mOutputItems[i]);
            } else if (GTUtility.areStacksEqual(this.mInventory[absi], this.mOutputItems[i])) {
                this.mInventory[absi].stackSize = Math.min(
                    this.mInventory[absi].getMaxStackSize() * mSetSlotSizeMultiplier,
                    this.mInventory[absi].stackSize + this.mOutputItems[i].stackSize);
            }
        }
    }

    private boolean spaceForOutput(ItemStack outputStack, int relativeOutputSlot) {
        int absoluteSlot = relativeOutputSlot + INPUT_SLOTS;
        if (this.mInventory[absoluteSlot] == null || outputStack == null) {
            return true;
        }
        return ((this.mInventory[absoluteSlot].stackSize + outputStack.stackSize
            <= this.mInventory[absoluteSlot].getMaxStackSize() * mSetSlotSizeMultiplier)
            && (GTUtility.areStacksEqual(this.mInventory[absoluteSlot], outputStack)));
    }

    private boolean checkRecipe() {
        if (!this.mMachine) {
            return false;
        }
        ItemStack[] inputs = new ItemStack[INPUT_SLOTS];
        System.arraycopy(mInventory, 0, inputs, 0, INPUT_SLOTS);
        GTRecipe recipe = getRecipeMap().findRecipeQuery()
            .items(inputs)
            .find();
        if (recipe == null) {
            this.mOutputItems = null;
            return false;
        }
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            if (!spaceForOutput(recipe.getOutput(i), i)) {
                this.mOutputItems = null;
                return false;
            }
        }

        if (!recipe.isRecipeInputEqual(true, null, inputs)) {
            this.mOutputItems = null;
            return false;
        }
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (mInventory[i] != null && mInventory[i].stackSize == 0) {
                mInventory[i] = null;
            }
        }

        this.mMaxProgresstime = recipe.mDuration / mSetRecipeAcceleration;
        this.mOutputItems = recipe.mOutputs;
        return true;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
                                  ItemStack aStack) {
        return aIndex > INPUT_SLOTS;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
                                 ItemStack aStack) {
        return !GTUtility.areStacksEqual(aStack, this.mInventory[0]);
    }

    @Override
    public byte getTileEntityBaseType() {
        return 0;
    }

    public abstract String getName();

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int build = 0;
        if (stackSize.stackSize == 1) {
            build = STRUCTURE_DEFINITION.get(getClass())
                .survivalBuild(
                    this,
                    stackSize,
                    tier1,
                    getBaseMetaTileEntity().getWorld(),
                    getExtendedFacing(),
                    getBaseMetaTileEntity().getXCoord(),
                    getBaseMetaTileEntity().getYCoord(),
                    getBaseMetaTileEntity().getZCoord(),
                    1,
                    4,
                    0,
                    elementBudget,
                    env,
                    false);
        } else if (stackSize.stackSize == 2) {
            build = STRUCTURE_DEFINITION.get(getClass())
                .survivalBuild(
                    this,
                    stackSize,
                    tier2,
                    getBaseMetaTileEntity().getWorld(),
                    getExtendedFacing(),
                    getBaseMetaTileEntity().getXCoord(),
                    getBaseMetaTileEntity().getYCoord(),
                    getBaseMetaTileEntity().getZCoord(),
                    2,
                    5,
                    0,
                    elementBudget,
                    env,
                    false);
        } else {
            build = STRUCTURE_DEFINITION.get(getClass())
                .survivalBuild(
                    this,
                    stackSize,
                    tier3,
                    getBaseMetaTileEntity().getWorld(),
                    getExtendedFacing(),
                    getBaseMetaTileEntity().getXCoord(),
                    getBaseMetaTileEntity().getYCoord(),
                    getBaseMetaTileEntity().getZCoord(),
                    2,
                    6,
                    0,
                    elementBudget,
                    env,
                    false);
        }
        return build;
    }

    @Override
    public IStructureDefinition<?> getStructureDefinition() {
        return STRUCTURE_DEFINITION.get(getClass());
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        if (stackSize.stackSize == 1) {
            STRUCTURE_DEFINITION.get(getClass())
                .buildOrHints(
                    this,
                    stackSize,
                    tier1,
                    getBaseMetaTileEntity().getWorld(),
                    getExtendedFacing(),
                    getBaseMetaTileEntity().getXCoord(),
                    getBaseMetaTileEntity().getYCoord(),
                    getBaseMetaTileEntity().getZCoord(),
                    1,
                    4,
                    0,
                    hintsOnly);
        } else if (stackSize.stackSize == 2) {
            STRUCTURE_DEFINITION.get(getClass())
                .buildOrHints(
                    this,
                    stackSize,
                    tier2,
                    getBaseMetaTileEntity().getWorld(),
                    getExtendedFacing(),
                    getBaseMetaTileEntity().getXCoord(),
                    getBaseMetaTileEntity().getYCoord(),
                    getBaseMetaTileEntity().getZCoord(),
                    2,
                    5,
                    0,
                    hintsOnly);
        } else {
            STRUCTURE_DEFINITION.get(getClass())
                .buildOrHints(
                    this,
                    stackSize,
                    tier3,
                    getBaseMetaTileEntity().getWorld(),
                    getExtendedFacing(),
                    getBaseMetaTileEntity().getXCoord(),
                    getBaseMetaTileEntity().getYCoord(),
                    getBaseMetaTileEntity().getZCoord(),
                    2,
                    6,
                    0,
                    hintsOnly);
        }

    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder
            .widget(
                new SlotWidget(inventoryHandler, 0)
                    .setBackground(
                        getGUITextureSet().getItemSlot(),
                        GTUITextures.OVERLAY_SLOT_INGOT_STEAM.get(getSteamVariant()))
                    .setPos(33, 15))
            .widget(
                new SlotWidget(inventoryHandler, 1)
                    .setBackground(
                        getGUITextureSet().getItemSlot(),
                        GTUITextures.OVERLAY_SLOT_DUST_STEAM.get(getSteamVariant()))
                    .setPos(33, 33))
            .widget(
                new SlotWidget(inventoryHandler, 2)
                    .setBackground(
                        getGUITextureSet().getItemSlot(),
                        GTUITextures.OVERLAY_SLOT_FURNACE_STEAM.get(getSteamVariant()))
                    .setPos(33, 51))
            .widget(
                new SlotWidget(inventoryHandler, 3).setAccess(true, false)
                    .setBackground(
                        getGUITextureSet().getItemSlot(),
                        GTUITextures.OVERLAY_SLOT_INGOT_STEAM.get(getSteamVariant()))
                    .setPos(85, 24))
            .widget(
                new SlotWidget(inventoryHandler, 4).setAccess(true, false)
                    .setBackground(
                        getGUITextureSet().getItemSlot(),
                        GTUITextures.OVERLAY_SLOT_DUST_STEAM.get(getSteamVariant()))
                    .setPos(103, 24))
            .widget(
                new SlotWidget(inventoryHandler, 5).setAccess(true, false)
                    .setBackground(
                        getGUITextureSet().getItemSlot(),
                        GTUITextures.OVERLAY_SLOT_DUST_STEAM.get(getSteamVariant()))
                    .setPos(121, 24))
            .widget(
                new ProgressBar().setTexture(GTUITextures.PROGRESSBAR_ARROW_2_STEAM.get(getSteamVariant()), 20)
                    .setProgress(() -> (float) mProgresstime / mMaxProgresstime)
                    .setNEITransferRect(
                        getRecipeMap().getFrontend()
                            .getUIProperties().neiTransferRectId)
                    .setPos(58, 24)
                    .setSize(20, 18));
    }

    @Override
    public GUITextureSet getGUITextureSet() {
        return GUITextureSet.STEAM.apply(getSteamVariant());
    }

    @Override
    public int getTitleColor() {
        return getSteamVariant() == SteamVariant.BRONZE ? COLOR_TITLE.get() : COLOR_TITLE_WHITE.get();
    }
}
