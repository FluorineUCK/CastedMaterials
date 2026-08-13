package com.fluorineuck.castedmaterials.material;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MaterialPairValidatorTest {
    @Test
    void keepsOnlySameIdBlockItemsPointingAtTheSameBlock() {
        ResourceLocation good = id("good");
        ResourceLocation wrong = id("wrong");
        MaterialValidationReport report = MaterialPairValidator.validate(
                Map.of(good, Items.STONE, wrong, Items.DIRT),
                Map.of(good, Blocks.STONE, wrong, Blocks.STONE),
                Set.of(good, wrong),
                Set.of(good, wrong));

        assertEquals(List.of(good), report.validIds());
        assertEquals(MaterialPairValidator.ErrorCode.BLOCK_ITEM_POINTS_ELSEWHERE,
                report.issues().getFirst().code());
        assertEquals(wrong, report.issues().getFirst().id());
    }

    @Test
    void requiresMembershipInBothTags() {
        ResourceLocation itemOnly = id("item_only");
        ResourceLocation blockOnly = id("block_only");
        MaterialValidationReport report = MaterialPairValidator.validate(
                Map.of(itemOnly, Items.STONE, blockOnly, Items.DIRT),
                Map.of(itemOnly, Blocks.STONE, blockOnly, Blocks.DIRT),
                Set.of(itemOnly),
                Set.of(blockOnly));

        assertEquals(List.of(), report.validIds());
        assertEquals(List.of(
                        MaterialPairValidator.ErrorCode.BLOCK_TAG_ONLY,
                        MaterialPairValidator.ErrorCode.ITEM_TAG_ONLY),
                report.issues().stream().map(MaterialValidationReport.Issue::code).toList());
    }

    @Test
    void rejectsMissingRegistryEntriesAndNonBlockItems() {
        ResourceLocation missingItem = id("a_missing_item");
        ResourceLocation missingBlock = id("b_missing_block");
        ResourceLocation plainItem = id("c_plain_item");

        MaterialValidationReport report = MaterialPairValidator.validate(
                Map.of(missingBlock, Items.STONE,
                        plainItem, Items.STICK),
                Map.of(missingItem, Blocks.STONE, plainItem, Blocks.DIRT),
                Set.of(missingItem, missingBlock, plainItem),
                Set.of(missingItem, missingBlock, plainItem));

        assertEquals(List.of(
                        MaterialPairValidator.ErrorCode.MISSING_ITEM,
                        MaterialPairValidator.ErrorCode.MISSING_BLOCK,
                        MaterialPairValidator.ErrorCode.NOT_BLOCK_ITEM),
                report.issues().stream().map(MaterialValidationReport.Issue::code).toList());
    }
}
