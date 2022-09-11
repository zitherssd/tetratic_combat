package smartin.tetraticcombat;

import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.bettercombat.api.AttributesContainer;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.api.WeaponAttributesHelper;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fml.loading.FMLConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.mickelus.tetra.effect.ItemEffect;
import se.mickelus.tetra.items.modular.ModularItem;
import se.mickelus.tetra.properties.AttributeHelper;
import smartin.tetraticcombat.network.SSyncConfig;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


public class Config {
    private static final Logger LOGGER = LogManager.getLogger();
    public static File jsonFile;
    public static String fileName = "bettercombatnbt.json";
    public static Map<String, Map<String, Condition>> JSON_MAP = new HashMap<>();


    public static void init(Path folder) {
        jsonFile = new File(FileUtils.getOrCreateDirectory(folder, "serverconfig").toFile(), fileName);
        try {
            if (jsonFile.createNewFile()) {
                Path defaultConfigPath = FMLPaths.GAMEDIR.get().resolve(FMLConfig.defaultConfigPath()).resolve(fileName);
                InputStreamReader defaults = new InputStreamReader(Files.exists(defaultConfigPath)? Files.newInputStream(defaultConfigPath) :
                        Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResourceAsStream("assets/fightnbtintegration/"+fileName)));
                FileOutputStream writer = new FileOutputStream(jsonFile, false);
                int read;
                while ((read = defaults.read()) != -1) {
                    writer.write(read);
                }
                writer.close();
                defaults.close();
            }
        } catch (IOException error) {
            LOGGER.warn(error.getMessage());
        }
        readConfig(jsonFile);
    }

    public static SSyncConfig configFileToSSyncConfig() {
        try {
            return new SSyncConfig(new String(Files.readAllBytes(jsonFile.toPath())));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void readConfig(String config) {
        JSON_MAP = new Gson().fromJson(config, new TypeToken<Map<String, Map<String, Condition>>>(){}.getType());
    }

    public static void readConfig(File path) {
        try (Reader reader = new FileReader(path)) {
            JSON_MAP = new Gson().fromJson(reader, new TypeToken<Map<String, Map<String, Condition>>>(){}.getType());
        } catch (IOException e) {
            e.printStackTrace();
            JSON_MAP = new HashMap<>();
        }
    }

    public static ExpandedContainer findWeaponByNBT(ItemStack stack) {
        if(stack.hasTag()) {
            CompoundTag tag = stack.getTag();
            for (String key : tag.getAllKeys()) {
                if(JSON_MAP.containsKey(key)){
                    Map<String, Condition> map1 =  JSON_MAP.get(key);
                    if(map1.containsKey(tag.getString(key))){
                        return map1.get(tag.getString(key)).resolve(stack);
                    }
                }
            }
        }
        return null;
    }

    public static ItemStack generateBetterCombatNBT(ItemStack itemStack){
        ExpandedContainer container = Config.findWeaponByNBT(itemStack);
        if(container!=null){
            try{
                double range = getAttackRange(itemStack);
                WeaponAttributes attributes = WeaponRegistry.resolveAttributes(new ResourceLocation("tetratic:generated"),container.attributes);
                if(ForgeConfigHolder.COMMON.EnableTetraRange.get()){
                    attributes =  new WeaponAttributes(range,attributes.pose(),attributes.offHandPose(),attributes.isTwoHanded(),attributes.category(),attributes.attacks());
                }
                RescaleUpswing(attributes,getQuickStat(itemStack));
                WeaponAttributesHelper.validate(attributes);
                AttributesContainer attributesContainer =  new AttributesContainer(container.attributes.parent(),attributes);
                WeaponAttributesHelper.writeToNBT(itemStack,attributesContainer);
                applyScale(itemStack,container.scaleX,container.scaleY,container.scaleZ);
                return itemStack;
            }
            catch (Exception e){
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        }
        return itemStack;
    }

    private static void applyScale(ItemStack stack,float x,float y,float z){
        CompoundTag nbt = stack.getTag();
        if(x!=1.0f)
            nbt.putFloat("tetraticScaleX",x);
        else if(nbt.contains("tetraticScaleX"))
            nbt.remove("tetraticScaleX");
        if(y!=1.0f)
            nbt.putFloat("tetraticScaleY",y);
        else if(nbt.contains("tetraticScaleY"))
            nbt.remove("tetraticScaleY");
        if(z!=1.0f)
            nbt.putFloat("tetraticScaleZ",z);
        else if(nbt.contains("tetraticScaleZ"))
            nbt.remove("tetraticScaleZ");
        stack.setTag(nbt);
    }

    private static double getQuickStat(ItemStack stack){
        if(stack.getItem() instanceof ModularItem item){
            //item.quickStrike
            //item.getEffectDataCache()

            var map = item.getEffectData(stack).levelMap;
            if(map.containsKey(ItemEffect.quickStrike)){
                float level = map.get(ItemEffect.quickStrike);
                return level*0.05d+0.2d;
            }
        }
        return 0.0d;
    }

    private static void RescaleUpswing(WeaponAttributes weaponAttributes, double scale){
        WeaponAttributes.Attack[] attacks = weaponAttributes.attacks();
        for(int i = 0;i<attacks.length;i++) {
            System.out.println("Old Upswing"+attacks[i].upswing());
            System.out.println("Scale"+scale);
            double newUpswing = Math.max(0,attacks[i].upswing()-attacks[i].upswing()*scale);
            System.out.println("new Upswing"+newUpswing);
            attacks[i] = new WeaponAttributes.Attack(
                    attacks[i].conditions(),
                    attacks[i].hitbox(),
                    attacks[i].damageMultiplier(),
                    attacks[i].angle(),
                    newUpswing,
                    attacks[i].animation(),
                    attacks[i].swingSound(),
                    attacks[i].impactSound()
            );
            //attacks.
        }
    }

    private static double getAttackRange(ItemStack itemStack){
        if(itemStack.getItem() instanceof ModularItem item){
            //TetraItem, use fallback to Reach
            if(item.getAttributeModifiers(itemStack.getEquipmentSlot(),itemStack).containsKey(ForgeMod.ATTACK_RANGE)){
                return 3.0d + item.getAttributeValue(itemStack, ForgeMod.ATTACK_RANGE.get());
            }
            else{
                return 3.0d + item.getAttributeValue(itemStack, ForgeMod.REACH_DISTANCE.get());
            }
        }
        else{
            //not a tetra Item, technically this code is not needed
            try{
                Multimap<Attribute, AttributeModifier> attributeMap = itemStack.getAttributeModifiers(itemStack.getEquipmentSlot());
                return AttributeHelper.getMergedAmount(attributeMap.get(ForgeMod.ATTACK_RANGE.get()),3.0d);
            }catch (Exception e){
                return 3.0d;
            }
        }
    }
}
