# LvNTM conversion report (generated)

Quests 1536, tasks 2169, rewards 1483.

Decisions taken: {by-name=66, component=379, derived=313, derived-renamed=4, flattened=28, from-dump=3, game-checked=17, inferred=49, kept=20, mapped=58, renamed=68, same=2039, same-ish=18, substitute=74, substitute-weak=10, unresolved=8}

## Importer problems

- chapter folder 7079924d is not listed in C:\Users\Lev\AppData\Roaming\PolyMC\instances\LvNTM\minecraft\config\ftbquests\normal\chapters\index.snbt; skipped, as FTB Quests does

## unresolved (8 distinct, 8 uses)

| from -> to | uses | note | first seen in |
|---|---|---|---|
| ore(decoBlock) ->  | 1 | no tag of that name in the 26.1.2 mod | quest Early Game / 0000000049843424 |
| ore(decoBlock) -> checkmark task | 1 | no 26.1.2 equivalent; the task became a check-off titled "Any decoBlock" | quest Early Game / 0000000049843424 |
| ore(bedrockOreHigherWeightPrimaryFraction) ->  | 1 | no tag of that name in the 26.1.2 mod | quest Bedrock Ore / Higher Weight Primary Fraction |
| ore(bedrockOreHigherWeightPrimaryFraction) -> checkmark task | 1 | no 26.1.2 equivalent; the task became a check-off titled "Any bedrockOreHigherWeightPrimaryFraction" | quest Bedrock Ore / Higher Weight Primary Fraction |
| ore(bedrockOreSeparated) ->  | 1 | no tag of that name in the 26.1.2 mod | quest Bedrock Ore / Separated Ore |
| ore(bedrockOreSeparated) -> checkmark task | 1 | no 26.1.2 equivalent; the task became a check-off titled "Any bedrockOreSeparated" | quest Bedrock Ore / Separated Ore |
| ore(bedrockOreLowerWeightPrimaryFraction) ->  | 1 | no tag of that name in the 26.1.2 mod | quest Bedrock Ore / Lower Weight Primary Fraction |
| ore(bedrockOreLowerWeightPrimaryFraction) -> checkmark task | 1 | no 26.1.2 equivalent; the task became a check-off titled "Any bedrockOreLowerWeightPrimaryFraction" | quest Bedrock Ore / Lower Weight Primary Fraction |

## substitute-weak (9 distinct, 10 uses)

| from -> to | uses | note | first seen in |
|---|---|---|---|
| ore(stoneResource) -> tag(c:cobblestones) | 1 | NTM's stoneResource has no tag in 26.1.2; cobblestones is the closest | quest LvNTM / Stone Resources |
| hbm:capsule_xen@0 -> hbm:crystal_xen | 1 | no Xen Capsule in 26.1.2; the Artificial Xen Crystal is the closest Xen item | quest Early Game / High-Performance Hazmat Suit |
| hbm:lung_diagnostic@0 -> hbm:digamma_diagnostic | 1 | no lung diagnostic item in 26.1.2 (the lung scanner is a GUI); the digamma diagnostic is the only diagnostic item | quest Early Game / 00000000E95D2814 |
| ore(container1000estradiol) -> items hbm:fluid_tank | 1 | the 1.12.2 filter meant a tank holding estradiol; the 26.1.2 filter here accepts any universal fluid tank (fluid cannot be tested with item()) | quest Oil Age / Gender Swapping Tactics |
| hbm:sat_interface@0 -> hbm:machine_satlink | 1 | the Satellite Control Interface item no longer exists; the ground station block replaces it | quest Satellites / Satellite Controls |
| refinedstorage:writer@0 -> refinedstorage:relay | 1 | Reader/Writer do not exist in 2.x; the Relay is the closest network-linking block; id exists in refinedstorage-neoforge-3.2.1 (26.1.2 instance, assets/refinedstorage/items) | quest Refined Storage / Writer and Reader |
| refinedstorage:reader@0 -> refinedstorage:relay | 1 | Reader/Writer do not exist in 2.x; the Relay is the closest network-linking block; id exists in refinedstorage-neoforge-3.2.1 (26.1.2 instance, assets/refinedstorage/items) | quest Refined Storage / Writer and Reader |
| hbm:spinny_light@0 -> hbm:lantern | 2 | the Spinny Light block does not exist in 26.1.2; the lantern is the closest light source | quest Checklists / 0000000076815E10 |
| ore(miniNuke) -> tag(hbm:bombs) | 1 | no mini nuke tag in 26.1.2; the bombs tag is broader | quest Weapons / 00000000B5258396 |

## substitute (23 distinct, 74 uses)

| from -> to | uses | note | first seen in |
|---|---|---|---|
| hbm:ingot_copper@0 -> minecraft:copper_ingot | 23 | the 1.12.2 "Industrial Grade Copper Ingot" is the vanilla copper ingot in 26.1.2 (tag c:ingots/copper); ingot_red_copper is the alloy | quest LvNTM / 000000009543BA13 |
| hbm:ore_copper@0 -> minecraft:copper_ore | 5 | 26.1.2 Nuclear Tech has no plain copper ore; vanilla copper ore is the equivalent | quest LvNTM / Copper |
| hbm:block_copper@0 -> minecraft:copper_block | 1 | 26.1.2 Nuclear Tech has no copper block; it uses the vanilla one (its c:ingots/copper tag lists minecraft:copper_ingot) | quest Early Game / 00000000887A3AED |
| refinedstorage:fluid_storage_part@0 -> refinedstorage:64b_fluid_storage_part | 1 | 1.6 fluid parts hold 64k; 2.x parts are 64B..4096B, smallest used; id exists in refinedstorage-neoforge-3.2.1 (26.1.2 instance, assets/refinedstorage/items) | quest Refined Storage / Fluids Parts |
| refinedstorage:grid@3 -> refinedstorage:grid | 1 | no separate Fluid Grid in 2.x: the Grid handles fluids; id exists in refinedstorage-neoforge-3.2.1 (26.1.2 instance, assets/refinedstorage/items) | quest Refined Storage / Fluid Grid |
| refinedstorage:disk_manipulator@0 -> refinedstorage:disk_interface | 1 | the Disk Manipulator was replaced by the Disk Interface; id exists in refinedstorage-neoforge-3.2.1 (26.1.2 instance, assets/refinedstorage/items) | quest Refined Storage / 0000000048C385AA |
| hbm:mp_warhead_15_thermo@0 -> hbm:mp_warhead_15_nuclear | 1 | no thermonuclear size-15 warhead in 26.1.2; the nuclear one is the closest | quest Missiles / 0000000028FC0BEB |
| hbm:mp_warhead_15_mirv@0 -> hbm:warhead_mirv | 1 | no size-15 MIRV warhead in 26.1.2; the MIRV warhead item is the closest | quest Missiles / 000000006F2F6CCB |
| ore(12 gauge) -> tag(hbm:ammo) | 6 | 26.1.2 has no per-caliber ammo tag; every NTM ammo item matches | quest Weapons / 000000000354DE87 |
| ore(capacitor (ammo)) -> tag(hbm:ammo) | 4 | 26.1.2 has no per-caliber ammo tag; every NTM ammo item matches | quest Weapons / 000000000A93525A |
| ore(9mm) -> tag(hbm:ammo) | 4 | 26.1.2 has no per-caliber ammo tag; every NTM ammo item matches | quest Weapons / 000000001598E79F |
| ore(.22 LR) -> tag(hbm:ammo) | 3 | 26.1.2 has no per-caliber ammo tag; every NTM ammo item matches | quest Weapons / 00000000193B6D40 |
| ore(rocket) -> tag(hbm:ammo) | 4 | 26.1.2 has no per-caliber ammo tag; every NTM ammo item matches | quest Weapons / 00000000370702E9 |
| ore(flamer fuel) -> tag(hbm:ammo) | 2 | 26.1.2 has no per-caliber ammo tag; every NTM ammo item matches | quest Weapons / 000000003A604099 |
| ore(10 gauge) -> tag(hbm:ammo) | 2 | 26.1.2 has no per-caliber ammo tag; every NTM ammo item matches | quest Weapons / 000000004A51FC07 |
| ore(.44) -> tag(hbm:ammo) | 3 | 26.1.2 has no per-caliber ammo tag; every NTM ammo item matches | quest Weapons / 0000000053D63BD0 |
| ore(40mm grenade) -> tag(hbm:ammo) | 2 | 26.1.2 has no per-caliber ammo tag; every NTM ammo item matches | quest Weapons / 000000005AA6B888 |
| ore(.50 BMG) -> tag(hbm:ammo) | 2 | 26.1.2 has no per-caliber ammo tag; every NTM ammo item matches | quest Weapons / 000000005E7111DE |
| ore(7.62mm) -> tag(hbm:ammo) | 2 | 26.1.2 has no per-caliber ammo tag; every NTM ammo item matches | quest Weapons / 000000007F9B0479 |
| ore(.357) -> tag(hbm:ammo) | 2 | 26.1.2 has no per-caliber ammo tag; every NTM ammo item matches | quest Weapons / 000000009BD34A9F |
| ore(5.56mm) -> tag(hbm:ammo) | 2 | 26.1.2 has no per-caliber ammo tag; every NTM ammo item matches | quest Weapons / 00000000C0DCC116 |
| hbm:bottle2_fritz_special@0 -> hbm:bottle2_fritz | 1 | the 1.12.2 "First Fritz Cola" collectible no longer exists; the normal Fritz Cola is the closest | reward table Treats |
| hbm:bottle2_korl_special@0 -> hbm:bottle2_korl | 1 | the 1.12.2 "First Korl" collectible no longer exists; the normal Korl is the closest | reward table Treats |

## inferred (39 distinct, 49 uses)

| from -> to | uses | note | first seen in |
|---|---|---|---|
| hbm:apple_lead@2 -> hbm:apple_lead_block | 2 | meta 1 and 2 of the apple are the ingot and block versions; the order is assumed | quest LvNTM / 00000000CE0D1E9C |
| hbm:apple_lead@1 -> hbm:apple_lead_ingot | 2 | meta 1 and 2 of the apple are the ingot and block versions; the order is assumed | quest LvNTM / 00000000CE0D1E9C |
| hbm:fluid_duct_mk2@1 -> hbm:fluid_duct_neo | 4 | the Universal Fluid Duct is fluid_duct_neo in 26.1.2 (colored and silver variants exist) | quest Early Game / 00000000039125AD |
| ore(drillbit) -> items hbm:drillbit_desh, hbm:drillbit_ferro, hbm:drillbit_hss, hbm:drillbit_steel, hbm:drillbit_tcalloy | 1 | drillbit_* items without the diamond tip | quest Oil Age / Drillbits |
| ore(tippedDrillbit) -> items hbm:drillbit_desh_diamond, hbm:drillbit_ferro_diamond, hbm:drillbit_hss_diamond, hbm:drillbit_steel_diamond, hbm:drillbit_tcalloy_diamond | 1 | drillbit_* items with the diamond tip | quest Oil Age / Tipped Drillbits |
| ore(bedrockOreRoastedPrimaryFraction) -> items hbm:bedrock_ore_primary_roasted_light, hbm:bedrock_ore_primary_roasted_heavy, hbm:bedrock_ore_primary_roasted_rare, hbm:bedrock_ore_primary_roasted_actinide, hbm:bedrock_ore_primary_roasted_nonmetal, hbm:bedrock_ore_primary_roasted_crystal | 1 | by name | quest Bedrock Ore / Roasted Primary Fraction |
| ore(bedrockOreDissolvedRoasted) -> items hbm:bedrock_ore_solvent_roasted_light, hbm:bedrock_ore_solvent_roasted_heavy, hbm:bedrock_ore_solvent_roasted_rare, hbm:bedrock_ore_solvent_roasted_actinide, hbm:bedrock_ore_solvent_roasted_nonmetal, hbm:bedrock_ore_solvent_roasted_crystal | 1 | Dissolved read as solvent from the naming pattern | quest Bedrock Ore / Dissolved Roasted |
| ore(bedrockOreDissolved) -> items hbm:bedrock_ore_solvent_byproduct_light, hbm:bedrock_ore_solvent_byproduct_heavy, hbm:bedrock_ore_solvent_byproduct_rare, hbm:bedrock_ore_solvent_byproduct_actinide, hbm:bedrock_ore_solvent_byproduct_nonmetal, hbm:bedrock_ore_solvent_byproduct_crystal | 1 | Dissolved read as solvent from the naming pattern | quest Bedrock Ore / Dissolved By-Product |
| ore(bedrockOrePrimaryFraction) -> items hbm:bedrock_ore_primary_light, hbm:bedrock_ore_primary_heavy, hbm:bedrock_ore_primary_rare, hbm:bedrock_ore_primary_actinide, hbm:bedrock_ore_primary_nonmetal, hbm:bedrock_ore_primary_crystal | 1 | by name | quest Bedrock Ore / Primary Fraction |
| ore(bedrockOreCleanedRoasted) -> items hbm:bedrock_ore_rad_roasted_light, hbm:bedrock_ore_rad_roasted_heavy, hbm:bedrock_ore_rad_roasted_rare, hbm:bedrock_ore_rad_roasted_actinide, hbm:bedrock_ore_rad_roasted_nonmetal, hbm:bedrock_ore_rad_roasted_crystal | 1 | Cleaned read as rad from the naming pattern | quest Bedrock Ore / Cleaned Roasted |
| ore(bedrockOreSulfuric) -> items hbm:bedrock_ore_sulfuric_byproduct_light, hbm:bedrock_ore_sulfuric_byproduct_heavy, hbm:bedrock_ore_sulfuric_byproduct_rare, hbm:bedrock_ore_sulfuric_byproduct_actinide, hbm:bedrock_ore_sulfuric_byproduct_nonmetal, hbm:bedrock_ore_sulfuric_byproduct_crystal | 1 | by name | quest Bedrock Ore / Sulfuric By-Product |
| ore(bedrockOreSulfuricPrimaryFraction) -> items hbm:bedrock_ore_primary_sulfuric_light, hbm:bedrock_ore_primary_sulfuric_heavy, hbm:bedrock_ore_primary_sulfuric_rare, hbm:bedrock_ore_primary_sulfuric_actinide, hbm:bedrock_ore_primary_sulfuric_nonmetal, hbm:bedrock_ore_primary_sulfuric_crystal | 1 | by name | quest Bedrock Ore / Sulfuric Primary Fraction |
| ore(bedrockOreDissolvedPrimaryFraction) -> items hbm:bedrock_ore_primary_solvent_light, hbm:bedrock_ore_primary_solvent_heavy, hbm:bedrock_ore_primary_solvent_rare, hbm:bedrock_ore_primary_solvent_actinide, hbm:bedrock_ore_primary_solvent_nonmetal, hbm:bedrock_ore_primary_solvent_crystal | 1 | Dissolved read as solvent from the naming pattern | quest Bedrock Ore / Dissolved Primary Fraction |
| ore(bedrockOreDissolvedSeared) -> items hbm:bedrock_ore_solvent_arc_light, hbm:bedrock_ore_solvent_arc_heavy, hbm:bedrock_ore_solvent_arc_rare, hbm:bedrock_ore_solvent_arc_actinide, hbm:bedrock_ore_solvent_arc_nonmetal, hbm:bedrock_ore_solvent_arc_crystal | 1 | Dissolved/Seared read as solvent/arc from the naming pattern | quest Bedrock Ore / Dissolved Seared |
| ore(bedrockOreSulfuricWashed) -> items hbm:bedrock_ore_sulfuric_washed_light, hbm:bedrock_ore_sulfuric_washed_heavy, hbm:bedrock_ore_sulfuric_washed_rare, hbm:bedrock_ore_sulfuric_washed_actinide, hbm:bedrock_ore_sulfuric_washed_nonmetal, hbm:bedrock_ore_sulfuric_washed_crystal | 1 | by name | quest Bedrock Ore / Sulfuric Washed |
| ore(bedrockOreCleanedPrimaryFraction) -> items hbm:bedrock_ore_primary_rad_light, hbm:bedrock_ore_primary_rad_heavy, hbm:bedrock_ore_primary_rad_rare, hbm:bedrock_ore_primary_rad_actinide, hbm:bedrock_ore_primary_rad_nonmetal, hbm:bedrock_ore_primary_rad_crystal | 1 | Cleaned read as rad from the naming pattern | quest Bedrock Ore / Cleaned Primary Fraction |
| ore(bedrockOreCleanedSeared) -> items hbm:bedrock_ore_rad_arc_light, hbm:bedrock_ore_rad_arc_heavy, hbm:bedrock_ore_rad_arc_rare, hbm:bedrock_ore_rad_arc_actinide, hbm:bedrock_ore_rad_arc_nonmetal, hbm:bedrock_ore_rad_arc_crystal | 1 | Cleaned/Seared read as rad/arc from the naming pattern | quest Bedrock Ore / Cleaned Seared |
| ore(bedrockOreSulfuricSeared) -> items hbm:bedrock_ore_sulfuric_arc_light, hbm:bedrock_ore_sulfuric_arc_heavy, hbm:bedrock_ore_sulfuric_arc_rare, hbm:bedrock_ore_sulfuric_arc_actinide, hbm:bedrock_ore_sulfuric_arc_nonmetal, hbm:bedrock_ore_sulfuric_arc_crystal | 1 | Seared read as arc from the naming pattern | quest Bedrock Ore / Sulfuric Seared |
| ore(bedrockOreSulfuricRoasted) -> items hbm:bedrock_ore_sulfuric_roasted_light, hbm:bedrock_ore_sulfuric_roasted_heavy, hbm:bedrock_ore_sulfuric_roasted_rare, hbm:bedrock_ore_sulfuric_roasted_actinide, hbm:bedrock_ore_sulfuric_roasted_nonmetal, hbm:bedrock_ore_sulfuric_roasted_crystal | 1 | by name | quest Bedrock Ore / Sulfuric Roasted |
| ore(bedrockOreDissolvedWashed) -> items hbm:bedrock_ore_solvent_washed_light, hbm:bedrock_ore_solvent_washed_heavy, hbm:bedrock_ore_solvent_washed_rare, hbm:bedrock_ore_solvent_washed_actinide, hbm:bedrock_ore_solvent_washed_nonmetal, hbm:bedrock_ore_solvent_washed_crystal | 1 | Dissolved read as solvent from the naming pattern | quest Bedrock Ore / Dissolved Washed |
| ore(bedrockOreCleanedWashed) -> items hbm:bedrock_ore_rad_washed_light, hbm:bedrock_ore_rad_washed_heavy, hbm:bedrock_ore_rad_washed_rare, hbm:bedrock_ore_rad_washed_actinide, hbm:bedrock_ore_rad_washed_nonmetal, hbm:bedrock_ore_rad_washed_crystal | 1 | Cleaned read as rad from the naming pattern | quest Bedrock Ore / Cleaned Washed |
| ore(bedrockOreCleaned) -> items hbm:bedrock_ore_rad_byproduct_light, hbm:bedrock_ore_rad_byproduct_heavy, hbm:bedrock_ore_rad_byproduct_rare, hbm:bedrock_ore_rad_byproduct_actinide, hbm:bedrock_ore_rad_byproduct_nonmetal, hbm:bedrock_ore_rad_byproduct_crystal | 1 | Cleaned read as rad from the naming pattern | quest Bedrock Ore / Cleaned By-Product |
| hbm:apple_schrabidium@1 -> hbm:apple_schrabidium_ingot | 2 | meta 1 and 2 of the apple are the ingot and block versions; the order is assumed | quest Late Game / 000000005A6CC8DF |
| hbm:apple_schrabidium@2 -> hbm:apple_schrabidium_block | 2 | meta 1 and 2 of the apple are the ingot and block versions; the order is assumed | quest Late Game / 000000005A6CC8DF |
| hbm:plant_flower@2 -> hbm:plant_flower_cd0 | 1 | Mustard Willow, first growth stage | quest Late Game / 32x Mustard Willow |
| hbm:plant_flower@3 -> hbm:plant_flower_cd1 | 1 | Mustard Willow, second growth stage | quest Late Game / Mustard Willow |
| hbm:plant_dead@0 -> hbm:plant_dead_generic | 1 | 26.1.2 has five dead-plant items with the same name; the generic one | quest Reactors / Mid Game Fuels |
| hbm:pile_rod@0 -> hbm:pile_rod_mk2_ra226be | 3 | order read from the quest structure: first the two neutron sources | quest Reactors / Chicago Pile Fuels |
| hbm:pile_rod@8 -> hbm:pile_rod_mk2_thorium_fuel | 1 | depends on the natural thorium rod | quest Reactors / 0000000031706DBC |
| hbm:book_guide_book@1 -> hbm:book_guide_rbmk | 2 | meta 1 is used by the quest "My first RBMK Guide Book"; the 26.1.2 mod has one guide book per topic | quest Reactors / My first RBMK Guide Book |
| hbm:pile_rod@4 -> hbm:pile_rod_mk2_pu239 | 1 | breeding chain uranium, plutonium, RGP, waste | quest Reactors / 000000007AADE212 |
| hbm:pile_rod@2 -> hbm:pile_rod_mk2_zr | 1 | the quest text speaks of zirconium rods | quest Reactors / 000000007F70C81E |
| hbm:pile_rod@5 -> hbm:pile_rod_mk2_rgp | 1 | breeding chain uranium, plutonium, RGP, waste | quest Reactors / 00000000AF5D6E4C |
| hbm:pile_rod@6 -> hbm:pile_rod_mk2_waste | 1 | breeding chain uranium, plutonium, RGP, waste | quest Reactors / 00000000B78F543E |
| hbm:pile_rod@1 -> hbm:pile_rod_mk2_po210be | 1 | order read from the quest structure: second neutron source | quest Reactors / Neutron Sources |
| refinedstorage:upgrade@7 -> refinedstorage:fortune_1_upgrade | 1 | fortune levels 7-9 taken as I-III; id exists in refinedstorage-neoforge-3.2.1 (26.1.2 instance, assets/refinedstorage/items) | quest Refined Storage / Destructor Upgrades |
| refinedstorage:upgrade@8 -> refinedstorage:fortune_2_upgrade | 1 | fortune levels 7-9 taken as I-III; id exists in refinedstorage-neoforge-3.2.1 (26.1.2 instance, assets/refinedstorage/items) | quest Refined Storage / Destructor Upgrades |
| refinedstorage:upgrade@9 -> refinedstorage:fortune_3_upgrade | 1 | fortune levels 7-9 taken as I-III; id exists in refinedstorage-neoforge-3.2.1 (26.1.2 instance, assets/refinedstorage/items) | quest Refined Storage / Destructor Upgrades |
| hbm:fluid_duct_exhaust@0 -> hbm:fluid_duct_exhaust_0 | 1 | 26.1.2 splits the exhaust pipe into five items; the first is used | quest Fluids / 00000000F90F6383 |

## kept (8 distinct, 20 uses)

| from -> to | uses | note | first seen in |
|---|---|---|---|
| lvimages:textures/image8.png -> lvimages:textures/image8.png | 1 | texture lives in the user's lvimage mod; ship it as a resource pack | chapter Oil Age |
| lvimages:textures/image2.png -> lvimages:textures/image2.png | 5 | texture lives in the user's lvimage mod; ship it as a resource pack | chapter Bedrock Ore |
| lvimages:textures/image3.png -> lvimages:textures/image3.png | 2 | texture lives in the user's lvimage mod; ship it as a resource pack | chapter Bedrock Ore |
| lvimages:textures/image4.png -> lvimages:textures/image4.png | 4 | texture lives in the user's lvimage mod; ship it as a resource pack | chapter Bedrock Ore |
| lvimages:textures/image5.png -> lvimages:textures/image5.png | 1 | texture lives in the user's lvimage mod; ship it as a resource pack | chapter Bedrock Ore |
| lvimages:textures/image1.png -> lvimages:textures/image1.png | 1 | texture lives in the user's lvimage mod; ship it as a resource pack | chapter Bedrock Ore |
| lvimages:textures/image6.png -> lvimages:textures/image6.png | 5 | texture lives in the user's lvimage mod; ship it as a resource pack | chapter Late Game |
| lvimages:textures/image9.png -> lvimages:textures/image9.png | 1 | texture lives in the user's lvimage mod; ship it as a resource pack | chapter Refined Storage |

## renamed (40 distinct, 68 uses)

| from -> to | uses | note | first seen in |
|---|---|---|---|
| minecraft:textures/items/barrier.png -> lvimages:textures/ntm_icon.png | 2 | texture folder renamed in 1.13 | chapter LvNTM |
| minecraft:textures/items/structure_void.png -> lvimages:textures/wojaksona.png | 1 | texture folder renamed in 1.13 | chapter LvNTM |
| futuremc:smoker@0 -> minecraft:smoker | 1 | Future MC block is vanilla in 26.1.2 | quest Vanilla / 00000000469BC620 |
| futuremc:grindstone@0 -> minecraft:grindstone | 1 | Future MC block is vanilla in 26.1.2 | quest Vanilla / 00000000469BC620 |
| hbm:fluid_tank_empty@0 -> hbm:fluid_tank | 3 | 26.1.2 has one universal tank item; empty means no fluid component | quest Early Game / Fluid Tanks |
| hbm:furnace_combination@0 -> hbm:machine_furnace_combination | 1 | "Combination Oven" is now "Combination Furnace" | quest Early Game / 0000000012D9934E |
| hbm:machine_wood_burner@0 -> hbm:machine_wood_burner | 3 | "Wood-Burning Generator" is now "Wood Burner" | quest Early Game / 000000001741DDA9 |
| hbm:insert_steel@0 -> hbm:insert_steel | 1 | "Heavy Steel Insert" is now "Steel Insert" | quest Early Game / 000000004AD0784B |
| hbm:radio_autocal@0 -> hbm:radio_autocal | 2 | "tile.radio_autocal.name" is now "AUTOCAL" | quest Early Game / 000000005010C1EC |
| hbm:fluid_barrel_empty@0 -> hbm:fluid_barrel | 2 | 26.1.2 has one barrel item; empty means no fluid component | quest Early Game / 000000008B1662A3 |
| hbm:red_pylon@0 -> hbm:red_pylon | 1 | "Electricity Pylon" is now "Wooden Electricity Pylon" | quest Early Game / Electricity Pylon |
| hbm:machine_mixer@0 -> hbm:machine_mixer | 2 | "Industrial Mixer" is now "Mixer" | quest Early Game / 00000000A1882161 |
| hbm:machine_furnace_brick_off@0 -> hbm:machine_furnace_brick_off | 2 | "Bricked Furnace" is now "Brick Furnace" | quest Early Game / 00000000A41D9F1E |
| hbm:heat_boiler@0 -> hbm:heat_boiler | 1 | "Large Boiler" is now "Heat Boiler" | quest Early Game / Boiler and Steam Condenser |
| hbm:red_pylon_steel@0 -> hbm:red_pylon_steel | 2 | "Medium Steel Electricity Pylon" is now "Steel Electricity Pylon" | quest Early Game / 00000000ECEDDBAD |
| hbm:insert_du@0 -> hbm:insert_du | 1 | "DU Insert" is now "Depleted Uranium Insert" | quest Oil Age / 000000009D2B6E2D |
| hbm:ore_bedrock_block@0 -> hbm:ore_bedrock_block | 4 | "Bedrock Ore" is now "Bedrock Ore Deposit" | quest Oil Age / 000000009EFF436C |
| hbm:machine_fluidtank@0 -> hbm:machine_fluidtank | 2 | "Tank" is now "Fluid Tank" | quest Oil Age / 00000000C75DEF26 |
| hbm:machine_turbine_gas@0 -> hbm:machine_turbine_gas | 2 | "Combined Cycle Gas Turbine" is now "Gas Turbine" | quest Oil Age / 00000000E302DA81 |
| hbm:machine_electrolyser@0 -> hbm:machine_electrolyser | 2 | "Electrolysis Machine" is now "Electrolyser" | quest Oil Age / 00000000F4C0E360 |
| hbm:block_starmetal@0 -> hbm:block_starmetal | 3 | "Block of Starmetal" is now "§9Block of Starmetal§r" | quest Late Game / Starmetal Tools |
| hbm:paa_legs@0 -> hbm:paa_legs | 1 | "PaA Leg Reinforcements" is now "PaA Leg Protection" | quest Late Game / 00000000139ED438 |
| hbm:paa_boots@0 -> hbm:paa_boots | 1 | "PaA "good ol' shoes"" is now "PaA Boots" | quest Late Game / 00000000139ED438 |
| hbm:ingot_starmetal@0 -> hbm:ingot_starmetal | 3 | "Starmetal Ingot" is now "§9Starmetal Ingot§r" | quest Late Game / 0000000047AEECCA |
| hbm:machine_radgen@0 -> hbm:machine_radgen | 2 | "Radiation-Powered Engine" is now "Radiation Generator" | quest Late Game / 0000000065679EF9 |
| hbm:fusion_breeder@0 -> hbm:fusion_breeder | 1 | "Fusion Reactor Breeding Chamber" is now "Fusion Reactor Breeder" | quest Late Game / 00000000670525A6 |
| hbm:scraps@0 -> hbm:scraps | 6 | "Stone Scraps" is now "Foundry Scraps" | quest Late Game / Bismoids |
| hbm:ring_starmetal@0 -> hbm:ring_starmetal | 1 | "Starmetal Ring" is now "§9Starmetal Ring§r" | quest Late Game / 00000000ABFC425A |
| hbm:machine_deuterium_tower@0 -> hbm:machine_deuterium_tower | 2 | "Deuterium Extraction Tower" is now "Heavy Water Extraction Tower" | quest Late Game / Deuterium Extractors |
| minecraft:textures/blocks/command_block_conditional.png -> lvimages:textures/gas_centrifuge.png | 1 | texture folder renamed in 1.13 | chapter Reactors |
| hbm:rbmk_fuel_drx@0 -> hbm:rbmk_fuel_drx | 1 | "Digamma RBMK Fuel Rod" is now "§cDigamma RBMK Fuel Rod§r" | quest Reactors / 00000000EB4863D6 |
| refinedstorage:crafter@0 -> refinedstorage:autocrafter | 1 | Crafter is Autocrafter in 2.x; id exists in refinedstorage-neoforge-3.2.1 (26.1.2 instance, assets/refinedstorage/items) | quest Refined Storage / 0000000002A689A1 |
| hbm:mass_storage_iron@0 -> hbm:mass_storage_iron | 1 | "Mass Storage Unit" is now "Iron Mass Storage" | quest Refined Storage / 00000000879AC4EA |
| hbm:mass_storage_wood@0 -> hbm:mass_storage_wood | 1 | "Mass Storage Unit" is now "Wooden Mass Storage" | quest Refined Storage / Wood Mass Storage Unit |
| refinedstorage:crafter_manager@0 -> refinedstorage:autocrafter_manager | 1 | id exists in refinedstorage-neoforge-3.2.1 (26.1.2 instance, assets/refinedstorage/items) | quest Refined Storage / 00000000D287DEA9 |
| refinedstorage:crafting_monitor@0 -> refinedstorage:autocrafting_monitor | 1 | id exists in refinedstorage-neoforge-3.2.1 (26.1.2 instance, assets/refinedstorage/items) | quest Refined Storage / 00000000DE5F31F5 |
| hbm:mass_storage_desh@0 -> hbm:mass_storage_desh | 1 | "Mass Storage Unit" is now "Desh Mass Storage" | quest Refined Storage / 00000000FD359A14 |
| hbm:machine_lpw2@0 -> hbm:machine_lpw2 | 2 | "LPW-2 Station Thruster" is now "LPW-2" | quest Checklists / 000000000C9B808B |
| hbm:machine_bigasstank@0 -> hbm:machine_bigasstank | 1 | "Big-Ass Tank" is now "Big Ass Tank" | quest Fluids / 00000000EB1CFE11 |
| hbm:armor_polish@0 -> hbm:armor_polish | 1 | "ShiningArmor™ Armor Polish" is now "ShiningArmor Armor Polish" | reward table Accessories |

## by-name (48 distinct, 66 uses)

| from -> to | uses | note | first seen in |
|---|---|---|---|
| hbm:plant_flower@0 -> hbm:plant_flower_foxglove | 4 | matched on the name "Foxglove" | quest LvNTM / 000000002A1E3E8F |
| hbm:plushie@2 -> hbm:plushie_numbernine | 1 | Number Nine Plushie | quest LvNTM / LvNTM |
| hbm:plant_flower@1 -> hbm:plant_flower_weed | 1 | Hemp (flower form) | quest LvNTM / 0000000078E797AB |
| hbm:stone_resource@0 -> hbm:stone_resource_sulfur | 1 | matched on the name "Sulfurous Stone" | quest LvNTM / Risky Stone |
| hbm:canned_conserve@20 -> hbm:canned_conserve_spam | 1 | matched on the name "Canned Spam" | quest LvNTM / Author |
| hbm:concrete_colored_ext@5 -> hbm:concrete_ext_hazard | 1 | "Builder's Choice Concrete - Hazard Stripe" | quest Early Game / Early Items and Machines |
| hbm:battery_pack@6 -> hbm:capacitor_copper | 3 | matched on the name "Copper Capacitor" | quest Early Game / 0000000005B623B0 |
| hbm:pump_steam@0 -> hbm:machine_pump_steam | 1 | matched on the name "Steam-Powered Groundwater Pump" | quest Early Game / 0000000046AA0110 |
| hbm:plushie@1 -> hbm:plushie_yomi | 1 | Yomi Plushie | quest Early Game / 000000005010C1EC |
| hbm:red_pylon_medium_transformer@0 -> hbm:red_pylon_medium_wood_transformer | 2 | matched on the name "Medium Wooden Electricity Pylon with Transformer" | quest Early Game / 000000005F6A1F9E |
| hbm:battery_pack@0 -> hbm:battery_redstone | 2 | matched on the name "Redstone Battery" | quest Early Game / 00000000871E8E6A |
| hbm:red_pylon_steel_small@0 -> hbm:red_pylon_steel | 2 | matched on the name "Steel Electricity Pylon" | quest Early Game / Electricity Pylon |
| hbm:crane_ejector@0 -> hbm:crane_extractor | 2 | matched on the name "Conveyor Ejector" | quest Early Game / 00000000D091A31A |
| hbm:red_pylon_steel_transformer@0 -> hbm:red_pylon_medium_steel_transformer | 2 | matched on the name "Medium Steel Electricity Pylon with Transformer" | quest Early Game / 00000000ECEDDBAD |
| hbm:icf_component@3 -> hbm:icf_component_structure | 1 | matched on the name "ICF Casing" | quest Late Game / Inertial Confinement Fusion Reactor (ICF) |
| hbm:icf_component@1 -> hbm:icf_component_vessel | 1 | matched on the name "ICF Containment Vessel Wall" | quest Late Game / Inertial Confinement Fusion Reactor (ICF) |
| hbm:battery_pack@10 -> hbm:capacitor_bismuth | 2 | matched on the name "Bismuth Capacitor" | quest Late Game / 0000000065DCE336 |
| hbm:icf_laser_component@0 -> hbm:icf_laser_component_casing | 1 | matched on the name "ICF Laser Casing" | quest Late Game / ICF Laser Components |
| hbm:icf_laser_component@1 -> hbm:icf_laser_component_port | 1 | matched on the name "ICF Laser Port" | quest Late Game / ICF Laser Components |
| hbm:icf_laser_component@2 -> hbm:icf_laser_component_cell | 1 | matched on the name "ICF Laser Cell" | quest Late Game / ICF Laser Components |
| hbm:icf_laser_component@3 -> hbm:icf_laser_component_emitter | 1 | matched on the name "ICF Laser Flash Tube" | quest Late Game / ICF Laser Components |
| hbm:icf_laser_component@4 -> hbm:icf_laser_component_capacitor | 1 | matched on the name "ICF Laser Capacitor" | quest Late Game / ICF Laser Components |
| hbm:icf_laser_component@5 -> hbm:icf_laser_component_turbo | 1 | matched on the name "ICF Laser Turbocharger" | quest Late Game / ICF Laser Components |
| hbm:plushie@3 -> hbm:plushie_hundun | 1 | Hundun Plushie | quest Late Game / 00000000B7AB17A7 |
| hbm:battery_pack@11 -> hbm:capacitor_spark | 2 | matched on the name "Spark Capacitor" | quest Late Game / 00000000BE06507D |
| hbm:missile_soyuz@0 -> hbm:missile_soyuz_0 | 1 | matched on the name "Soyuz-FG" | quest Late Game / Soyuz Rocket and Launch Pad |
| hbm:fusion_component@3 -> hbm:fusion_component_motor | 1 | matched on the name "Fusion Reactor Piping" | quest Late Game / Fusion Reactor Vessel |
| hbm:fusion_component@2 -> hbm:fusion_component_bscco_welded | 1 | matched on the name "Welded BSCCO Coils" | quest Late Game / Fusion Reactor Vessel |
| hbm:rbmk_rod@0 -> hbm:rbmk_fuel_rod | 1 | matched on the name "RBMK Fuel Channel" | quest Reactors / RBMK Nuclear Reactor |
| hbm:bolt@33 -> hbm:bolt_dura_steel | 1 | "High-Speed Steel Bolt"; hbmmat.dura_steel is called High-Speed Steel | quest Reactors / Watz Powerplant |
| hbm:pwr_fuelrod@0 -> hbm:pwr_fuel | 1 | matched on the name "PWR Fuel Rod" | quest Reactors / PWR Reactor |
| hbm:mp_stability_20_flat@0 -> hbm:mp_s_20 | 1 | matched on the name "Size 20 Flat Fins" | quest Missiles / 0000000083BFCE86 |
| hbm:battery_pack@4 -> hbm:battery_schrabidium | 1 | matched on the name "Schrabidium Battery" | quest Batteries / 000000000E4573E5 |
| hbm:battery_pack@2 -> hbm:battery_lithium | 1 | matched on the name "Lithium-Ion Battery" | quest Batteries / 0000000020414F81 |
| hbm:battery_pack@7 -> hbm:capacitor_gold | 1 | matched on the name "Gold Capacitor" | quest Batteries / 0000000034174482 |
| hbm:battery_pack@5 -> hbm:battery_quantum | 2 | matched on the name "Quantum Battery" | quest Batteries / 000000004A13C931 |
| hbm:battery_pack@1 -> hbm:battery_lead | 1 | matched on the name "Lead-Acid Battery" | quest Batteries / 00000000B3DACA76 |
| hbm:battery_pack@8 -> hbm:capacitor_niobium | 1 | matched on the name "Niobium Capacitor" | quest Batteries / 00000000CC0D4CA0 |
| hbm:battery_pack@3 -> hbm:battery_sodium | 1 | matched on the name "Sodium-Iron Battery" | quest Batteries / 00000000EBC1CF60 |
| hbm:battery_pack@9 -> hbm:capacitor_tantalum | 1 | "Tantalum Capacitor"; the 26.1.2 mod lists capacitor_tantalum (and a misspelt capacitor_tantalium) | quest Batteries / 00000000EE69C6BA |
| hbm:ore_meteor@2 -> hbm:ore_meteor_aluminium | 2 | matched on the name "Meteor Aluminium Ore" | quest Ores / 000000000EEA1185 |
| hbm:ore_meteor@0 -> hbm:ore_meteor_iron | 2 | matched on the name "Meteor Iron Ore" | quest Ores / 0000000031FB98DB |
| hbm:ore_meteor@4 -> hbm:ore_meteor_cobalt | 2 | matched on the name "Meteor Cobalt Ore" | quest Ores / 00000000B9E29804 |
| hbm:ore_meteor@1 -> hbm:ore_meteor_copper | 2 | matched on the name "Meteor Copper Ore" | quest Ores / 00000000E073F636 |
| hbm:ore_meteor@3 -> hbm:ore_meteor_rareearth | 2 | matched on the name "Meteor Rare Earth Ore" | quest Ores / 00000000EB047545 |
| hbm:pribris_radiating@0 -> hbm:rbmk_debris_radiating | 1 | matched on the name "Smoldering RBMK Debris" | quest Bosses / 0000000099EB4C69 |
| hbm:pribris@0 -> hbm:rbmk_debris | 1 | matched on the name "RBMK Debris" | quest Bosses / 00000000E81B73B6 |
| hbm:canned_conserve@0 -> hbm:canned_conserve_beef | 1 | matched on the name "Canned Beef" | reward table Treats |

## derived (224 distinct, 313 uses)

| from -> to | uses | note | first seen in |
|---|---|---|---|
| hbm:plant_item@1 -> hbm:plant_item_rope | 2 |  | quest LvNTM / 0000000078E797AB |
| ore(treeSapling) -> tag(minecraft:saplings) | 1 | vanilla sapling tag | quest LvNTM / 000000009B7F0D9F |
| hbm:chunk_ore@2 -> hbm:chunk_ore_cryolite | 7 |  | quest LvNTM / 00000000AF06E928 |
| hbm:ammo_standard@0 -> hbm:ammo_standard_stone | 13 |  | quest LvNTM / Pepperbox |
| ore(logWood) -> tag(minecraft:logs) | 1 | vanilla log tag | quest Vanilla / So it begins! |
| ore(oreCobalt) -> tag(c:ores/cobalt) | 1 |  | quest Early Game / 0000000002AD9546 |
| hbm:crashed_bomb@3 -> hbm:crashed_bomb_salted | 1 | tile.crashed_bomb.salted | quest Early Game / 00000000165103AE |
| hbm:crashed_bomb@1 -> hbm:crashed_bomb_conventional | 1 | tile.crashed_bomb.conventional | quest Early Game / 00000000165103AE |
| ore(gemCoal) -> tag(c:gems/coal) | 1 | if the tag does not exist the entry falls to unresolved at export | quest Early Game / Black Lung and Mesothelioma, Second Revision Edition |
| hbm:ammo_standard@50 -> hbm:ammo_standard_g26_flare | 1 |  | quest Early Game / Iron Anvil |
| ore(bobblehead) -> items hbm:bobblehead_abel, hbm:bobblehead_adam29, hbm:bobblehead_agility, hbm:bobblehead_bluehat, hbm:bobblehead_bob, hbm:bobblehead_charisma, hbm:bobblehead_cirno, hbm:bobblehead_clawd, hbm:bobblehead_doc, hbm:bobblehead_drillgon, hbm:bobblehead_endurance, hbm:bobblehead_eos, hbm:bobblehead_frizzle, hbm:bobblehead_golem, hbm:bobblehead_hacker6329, hbm:bobblehead_intelligence, hbm:bobblehead_leafia, hbm:bobblehead_luck, hbm:bobblehead_mellow, hbm:bobblehead_microwave, hbm:bobblehead_movblock, hbm:bobblehead_norwood, hbm:bobblehead_nos, hbm:bobblehead_peep, hbm:bobblehead_perception, hbm:bobblehead_pheo, hbm:bobblehead_pu238, hbm:bobblehead_silentyeti, hbm:bobblehead_strength, hbm:bobblehead_uffr, hbm:bobblehead_vaer, hbm:bobblehead_vt | 1 | every bobblehead_* item | quest Early Game / 000000003B5EFBA7 |
| hbm:circuit@7 -> hbm:circuit_analog | 1 |  | quest Early Game / 000000008A25FA82 |
| hbm:conveyor_wand@0 -> hbm:conveyor_wand_regular | 5 |  | quest Early Game / 00000000A6972699 |
| hbm:circuit@8 -> hbm:circuit_basic | 5 |  | quest Early Game / Old Lantern |
| hbm:weapon_mod_test@1 -> hbm:weapon_mod_test_damage | 1 |  | quest Early Game / Hazards |
| hbm:grenade_shell@0 -> hbm:grenade_shell_frag | 3 |  | quest Early Game / 00000000FA871A76 |
| hbm:grenade_shell@1 -> hbm:grenade_shell_stick | 3 |  | quest Early Game / 00000000FA871A76 |
| hbm:circuit@9 -> hbm:circuit_advanced | 3 |  | quest Oil Age / 0000000005301D44 |
| hbm:coke@2 -> hbm:coke_petroleum | 1 |  | quest Oil Age / 000000002087DC03 |
| hbm:battery_sc@1 -> hbm:battery_sc_waste | 2 |  | quest Oil Age / 000000002D1B2819 |
| hbm:circuit@3 -> hbm:circuit_pcb | 1 |  | quest Oil Age / 000000008931814A |
| hbm:circuit@5 -> hbm:circuit_chip | 1 |  | quest Oil Age / 00000000A14582DA |
| hbm:circuit@13 -> hbm:circuit_controller | 1 |  | quest Oil Age / 00000000B7C79B3D |
| hbm:piston_set@0 -> hbm:piston_set_steel | 8 |  | quest Oil Age / 00000000C6E020E4 |
| hbm:powder_ash@4 -> hbm:powder_ash_soot | 1 |  | quest Oil Age / 00000000FE8D7E5A |
| hbm:bedrock_ore_fragment@7400 -> hbm:bedrock_ore_fragment_tungsten | 1 |  | quest Bedrock Ore / Dissolved Heavy Metal Results |
| hbm:bedrock_ore_fragment@8200 -> hbm:bedrock_ore_fragment_lead | 1 |  | quest Bedrock Ore / Dissolved Heavy Metal Results |
| hbm:bedrock_ore_fragment@7900 -> hbm:bedrock_ore_fragment_gold | 3 |  | quest Bedrock Ore / Dissolved Heavy Metal Results |
| hbm:bedrock_ore_new@209 -> hbm:bedrock_ore_crumbs_heavy | 3 | name "Heavy Metal Bedrock Ore Crumbs" | quest Bedrock Ore / Dissolved Heavy Metal Results |
| hbm:bedrock_ore_new@0 -> hbm:bedrock_ore_base_light | 1 | name "Light Metal Bedrock Ore" | quest Bedrock Ore / 0000000013D6A9A1 |
| hbm:bedrock_ore_fragment@1701 -> hbm:bedrock_ore_fragment_chlorocalcite | 3 |  | quest Bedrock Ore / Dissolved Light Metal Results |
| hbm:bedrock_ore_fragment@300 -> hbm:bedrock_ore_fragment_lithium | 2 |  | quest Bedrock Ore / Dissolved Light Metal Results |
| hbm:bedrock_ore_fragment@1100 -> hbm:bedrock_ore_fragment_sodium | 2 |  | quest Bedrock Ore / Dissolved Light Metal Results |
| hbm:bedrock_ore_new@208 -> hbm:bedrock_ore_crumbs_light | 3 | name "Light Metal Bedrock Ore Crumbs" | quest Bedrock Ore / Dissolved Light Metal Results |
| hbm:bedrock_ore_new@4 -> hbm:bedrock_ore_base_nonmetal | 1 | name "Non-Metal Bedrock Ore" | quest Bedrock Ore / 0000000023A63F65 |
| hbm:bedrock_ore_new@5 -> hbm:bedrock_ore_base_crystal | 1 | name "Crystalline Bedrock Ore" | quest Bedrock Ore / 00000000298FC722 |
| hbm:bedrock_ore_fragment@400 -> hbm:bedrock_ore_fragment_beryllium | 1 |  | quest Bedrock Ore / Sulfuric Heavy Metal Results |
| hbm:bedrock_ore_fragment@1500 -> hbm:bedrock_ore_fragment_red_phosphorus | 1 | name "Red Phosphorus Bedrock Ore Fragment" | quest Bedrock Ore / Dissolved Non-Metal Results |
| hbm:bedrock_ore_fragment@900 -> hbm:bedrock_ore_fragment_fluorite | 2 |  | quest Bedrock Ore / Dissolved Non-Metal Results |
| hbm:bedrock_ore_fragment@1600 -> hbm:bedrock_ore_fragment_sulfur | 1 |  | quest Bedrock Ore / Dissolved Non-Metal Results |
| hbm:bedrock_ore_new@212 -> hbm:bedrock_ore_crumbs_nonmetal | 3 | name "Non-Metal Bedrock Ore Crumbs" | quest Bedrock Ore / Dissolved Non-Metal Results |
| hbm:bedrock_ore_fragment@8826 -> hbm:bedrock_ore_fragment_ra226 | 2 | name "Radium-226 Bedrock Ore Fragment" | quest Bedrock Ore / Dissolved Actinide Results |
| hbm:bedrock_ore_fragment@8410 -> hbm:bedrock_ore_fragment_polonium | 2 | name "Polonium-210 Bedrock Ore Fragment" | quest Bedrock Ore / Dissolved Actinide Results |
| hbm:bedrock_ore_new@211 -> hbm:bedrock_ore_crumbs_actinide | 3 | name "Actinide Bedrock Ore Crumbs" | quest Bedrock Ore / Dissolved Actinide Results |
| hbm:bedrock_ore_new@1 -> hbm:bedrock_ore_base_heavy | 1 | name "Heavy Metal Bedrock Ore" | quest Bedrock Ore / 0000000035A1F284 |
| hbm:bedrock_ore_new@3 -> hbm:bedrock_ore_base_actinide | 1 | name "Actinide Bedrock Ore" | quest Bedrock Ore / 0000000038803324 |
| hbm:bedrock_ore_fragment@8001 -> hbm:bedrock_ore_fragment_cinnabar | 1 |  | quest Bedrock Ore / Dissolved Crystalline Results |
| hbm:bedrock_ore_fragment@1401 -> hbm:bedrock_ore_fragment_asbestos | 2 |  | quest Bedrock Ore / Dissolved Crystalline Results |
| hbm:bedrock_ore_fragment@401 -> hbm:bedrock_ore_fragment_emerald | 1 |  | quest Bedrock Ore / Dissolved Crystalline Results |
| hbm:bedrock_ore_new@213 -> hbm:bedrock_ore_crumbs_crystal | 3 | name "Crystalline Bedrock Ore Crumbs" | quest Bedrock Ore / Dissolved Crystalline Results |
| hbm:bedrock_ore_fragment@601 -> hbm:bedrock_ore_fragment_lignite | 1 |  | quest Bedrock Ore / Sulfuric Non-Metal Results |
| hbm:bedrock_ore_fragment@700 -> hbm:bedrock_ore_fragment_niter | 1 | name "Niter Bedrock Ore Fragment" | quest Bedrock Ore / Sulfuric Non-Metal Results |
| hbm:bedrock_ore_fragment@8300 -> hbm:bedrock_ore_fragment_bismuth | 1 |  | quest Bedrock Ore / Cleaned Heavy Metal Results |
| hbm:bedrock_ore_fragment@7300 -> hbm:bedrock_ore_fragment_tantalium | 1 | name "Tantalum Bedrock Ore Fragment" | quest Bedrock Ore / Cleaned Heavy Metal Results |
| ore(bedrockOreFullWashed) -> items hbm:bedrock_ore_base_washed_light, hbm:bedrock_ore_base_washed_heavy, hbm:bedrock_ore_base_washed_rare, hbm:bedrock_ore_base_washed_actinide, hbm:bedrock_ore_base_washed_nonmetal, hbm:bedrock_ore_base_washed_crystal | 1 | items named "Washed <kind> Bedrock Ore" in the 1.12.2 dump | quest Bedrock Ore / Washed Ore |
| hbm:bedrock_ore_fragment@1400 -> hbm:bedrock_ore_fragment_silicon | 1 |  | quest Bedrock Ore / Cleaned Non-Metal Results |
| hbm:bedrock_ore_fragment@4399 -> hbm:bedrock_ore_fragment_technetium | 1 | name "Technetium-99 Bedrock Ore Fragment" | quest Bedrock Ore / Cleaned Actinide Results |
| hbm:bedrock_ore_fragment@9238 -> hbm:bedrock_ore_fragment_u238 | 1 | name "Uranium-238 Bedrock Ore Fragment" | quest Bedrock Ore / Cleaned Actinide Results |
| hbm:bedrock_ore_new@2 -> hbm:bedrock_ore_base_rare | 1 | name "Rare Earth Bedrock Ore" | quest Bedrock Ore / 000000008F798DA2 |
| hbm:bedrock_ore_fragment@2200 -> hbm:bedrock_ore_fragment_titanium | 1 |  | quest Bedrock Ore / Sulfuric Light Metal Results |
| hbm:bedrock_ore_fragment@2902 -> hbm:bedrock_ore_fragment_bauxite | 1 |  | quest Bedrock Ore / Sulfuric Light Metal Results |
| hbm:bedrock_ore_fragment@2903 -> hbm:bedrock_ore_fragment_cryolite | 1 |  | quest Bedrock Ore / Sulfuric Light Metal Results |
| hbm:bedrock_ore_fragment@501 -> hbm:bedrock_ore_fragment_borax | 1 |  | quest Bedrock Ore / Cleaned Crystalline Results |
| hbm:bedrock_ore_fragment@1101 -> hbm:bedrock_ore_fragment_sodalite | 2 |  | quest Bedrock Ore / Cleaned Crystalline Results |
| hbm:bedrock_ore_fragment@1702 -> hbm:bedrock_ore_fragment_molysite | 1 |  | quest Bedrock Ore / Cleaned Crystalline Results |
| hbm:bedrock_ore_fragment@6000 -> hbm:bedrock_ore_fragment_neodymium | 2 |  | quest Bedrock Ore / Dissolved Rare Earth Ore Results |
| hbm:bedrock_ore_fragment@3800 -> hbm:bedrock_ore_fragment_strontium | 2 |  | quest Bedrock Ore / Dissolved Rare Earth Ore Results |
| hbm:bedrock_ore_fragment@4000 -> hbm:bedrock_ore_fragment_zirconium | 1 |  | quest Bedrock Ore / Dissolved Rare Earth Ore Results |
| hbm:bedrock_ore_new@210 -> hbm:bedrock_ore_crumbs_rare | 3 | name "Rare Earth Bedrock Ore Crumbs" | quest Bedrock Ore / Dissolved Rare Earth Ore Results |
| hbm:bedrock_ore_fragment@1430 -> hbm:bedrock_ore_fragment_diamond | 1 |  | quest Bedrock Ore / Sulfuric Crystalline Results |
| hbm:bedrock_ore_fragment@500 -> hbm:bedrock_ore_fragment_boron | 1 |  | quest Bedrock Ore / Sulfuric Rare Earth Ore Results |
| hbm:bedrock_ore_fragment@5700 -> hbm:bedrock_ore_fragment_lanthanium | 1 | name "Lanthanium Bedrock Ore Fragment" | quest Bedrock Ore / Sulfuric Rare Earth Ore Results |
| hbm:bedrock_ore_fragment@4100 -> hbm:bedrock_ore_fragment_niobium | 2 |  | quest Bedrock Ore / Sulfuric Rare Earth Ore Results |
| hbm:circuit@6 -> hbm:circuit_chip_bismoid | 3 |  | quest Late Game / 000000000B942879 |
| hbm:ammo_standard@47 -> hbm:ammo_standard_g12_magnum | 1 |  | quest Late Game / 000000001B55D1C9 |
| hbm:pa_coil@2 -> hbm:pa_coil_bscco | 2 |  | quest Late Game / 000000003679809F |
| hbm:pa_coil@0 -> hbm:pa_coil_gold | 3 |  | quest Late Game / 000000004341AA70 |
| hbm:pa_coil@1 -> hbm:pa_coil_niobium | 2 |  | quest Late Game / 0000000048E586F6 |
| hbm:ammo_standard@70 -> hbm:ammo_standard_tau_uranium | 2 |  | quest Late Game / 000000004A0CB542 |
| ore(boltDuraSteel) -> tag(c:bolts/dura_steel) | 1 |  | quest Late Game / Inertial Confinement Fusion Reactor (ICF) |
| ore(plateTripleSteel) -> tag(c:plates_cast/steel) | 2 |  | quest Late Game / Inertial Confinement Fusion Reactor (ICF) |
| ore(plateTripleAnyBismoidBronze) -> tag(c:plates_cast/any_bismoid_bronze) | 1 |  | quest Late Game / Inertial Confinement Fusion Reactor (ICF) |
| hbm:circuit@11 -> hbm:circuit_bismoid | 2 |  | quest Late Game / Inertial Confinement Fusion Reactor (ICF) |
| hbm:circuit@15 -> hbm:circuit_quantum | 2 |  | quest Late Game / Inertial Confinement Fusion Reactor (ICF) |
| hbm:powder_ash@5 -> hbm:powder_ash_fullerene | 3 |  | quest Late Game / 000000006289E39E |
| ore(ingotAnyResistantAlloy) -> tag(c:ingots/any_resistant_alloy) | 1 |  | quest Late Game / x32 Any Resistant Alloy Ingots |
| ore(ingotBSCCO) -> tag(c:ingots/bscco) | 1 |  | quest Late Game / 0000000083C65807 |
| hbm:circuit@14 -> hbm:circuit_controller_advanced | 1 |  | quest Late Game / 00000000B5BDADF2 |
| ore(ingotAnyHardPlastic) -> tag(c:ingots/any_hardplastic) | 1 | tag name has no underscore in 26.1.2 | quest Late Game / Plastic Man |
| ore(ingotAnyBismoidBronze) -> tag(c:ingots/any_bismoid_bronze) | 2 |  | quest Late Game / Bronze Bismoids |
| hbm:plant_item@2 -> hbm:plant_item_mustardwillow | 1 |  | quest Late Game / Mustard Willow |
| hbm:circuit@16 -> hbm:circuit_chip_quantum | 1 |  | quest Late Game / 00000000F37C9249 |
| hbm:circuit@17 -> hbm:circuit_controller_quantum | 1 |  | quest Late Game / 00000000F4DC6388 |
| hbm:rod_zirnox@7 -> hbm:rod_zirnox_u235_fuel | 1 |  | quest Reactors / 0000000003013B4A |
| hbm:rod_zirnox@2 -> hbm:rod_zirnox_th232_fuel | 1 |  | quest Reactors / 0000000006D2576B |
| hbm:battery_sc@5 -> hbm:battery_sc_pu238 | 1 |  | quest Reactors / 00000000121C35A3 |
| hbm:battery_sc@4 -> hbm:battery_sc_co60 | 1 |  | quest Reactors / 0000000026EA04C5 |
| hbm:pile_device@0 -> hbm:pile_loader | 1 | tile.pile_device_0 = Chicago Pile Fuel Loader | quest Reactors / Chicago Pile |
| hbm:pile_device@1 -> hbm:pile_vent | 1 | tile.pile_device_1 = Chicago Pile Vent | quest Reactors / Chicago Pile |
| hbm:pile_device@2 -> hbm:pile_control | 1 | tile.pile_device_2 = Chicago Pile Control Rod | quest Reactors / Chicago Pile |
| hbm:rod_zirnox@0 -> hbm:rod_zirnox_natural_uranium_fuel | 1 |  | quest Reactors / 000000002F5B5381 |
| hbm:battery_sc@9 -> hbm:battery_sc_am241 | 1 |  | quest Reactors / 00000000320484D4 |
| hbm:battery_sc@2 -> hbm:battery_sc_ra226 | 1 |  | quest Reactors / 0000000032F26A2F |
| hbm:rod_zirnox@5 -> hbm:rod_zirnox_plutonium_fuel | 1 |  | quest Reactors / 00000000343B85E5 |
| hbm:watz_pellet_depleted@4 -> hbm:watz_pellet_depleted_hen | 1 |  | quest Reactors / 0000000038BF5281 |
| hbm:watz_pellet_depleted@2 -> hbm:watz_pellet_depleted_mes | 1 |  | quest Reactors / 000000004224B729 |
| hbm:oil_tar@4 -> hbm:oil_tar_wax | 1 |  | quest Reactors / 0000000051665BEB |
| hbm:battery_sc@7 -> hbm:battery_sc_au198 | 1 |  | quest Reactors / 00000000547DC44E |
| hbm:watz_pellet@7 -> hbm:watz_pellet_lead | 1 |  | quest Reactors / Absorber Pellets |
| hbm:watz_pellet@9 -> hbm:watz_pellet_du | 1 |  | quest Reactors / Absorber Pellets |
| hbm:watz_pellet@8 -> hbm:watz_pellet_boron | 1 |  | quest Reactors / Absorber Pellets |
| hbm:battery_sc@6 -> hbm:battery_sc_po210 | 1 |  | quest Reactors / 000000006390669A |
| hbm:rod_zirnox@4 -> hbm:rod_zirnox_mox_fuel | 1 |  | quest Reactors / 0000000065048C00 |
| hbm:watz_pellet@3 -> hbm:watz_pellet_les | 1 |  | quest Reactors / 000000006FAE9330 |
| hbm:battery_sc@8 -> hbm:battery_sc_pb209 | 1 |  | quest Reactors / 000000007B3F7018 |
| hbm:watz_pellet@4 -> hbm:watz_pellet_hen | 1 |  | quest Reactors / 0000000081751619 |
| hbm:rod_zirnox@10 -> hbm:rod_zirnox_zfb_mox_fuel | 1 |  | quest Reactors / 00000000A1EB7625 |
| hbm:watz_pellet_depleted@6 -> hbm:watz_pellet_depleted_mep | 1 |  | quest Reactors / 00000000A603711C |
| hbm:battery_sc@3 -> hbm:battery_sc_tc99 | 1 |  | quest Reactors / 00000000A6B1377E |
| hbm:watz_pellet@5 -> hbm:watz_pellet_meu | 1 |  | quest Reactors / 00000000AD00572D |
| hbm:watz_pellet_depleted@7 -> hbm:watz_pellet_depleted_lead | 1 |  | quest Reactors / Depleted Absorber Pellets |
| hbm:watz_pellet_depleted@8 -> hbm:watz_pellet_depleted_boron | 1 |  | quest Reactors / Depleted Absorber Pellets |
| hbm:watz_pellet_depleted@9 -> hbm:watz_pellet_depleted_du | 1 |  | quest Reactors / Depleted Absorber Pellets |
| hbm:battery_sc@0 -> hbm:battery_sc_empty | 1 |  | quest Reactors / 00000000BE64A57F |
| hbm:watz_pellet@2 -> hbm:watz_pellet_mes | 1 |  | quest Reactors / 00000000C5D7F435 |
| hbm:rod_zirnox@9 -> hbm:rod_zirnox_lithium_fuel | 1 |  | quest Reactors / 00000000CC0A8F0C |
| hbm:watz_pellet_depleted@0 -> hbm:watz_pellet_depleted_schrabidium | 1 |  | quest Reactors / 00000000CFC95E41 |
| hbm:watz_pellet_depleted@3 -> hbm:watz_pellet_depleted_les | 1 |  | quest Reactors / 00000000D5F805FE |
| hbm:rod_zirnox@8 -> hbm:rod_zirnox_les_fuel | 1 |  | quest Reactors / 00000000DCAD56EC |
| hbm:rod_zirnox@1 -> hbm:rod_zirnox_uranium_fuel | 1 |  | quest Reactors / 00000000E3BCAD20 |
| hbm:rod_zirnox@3 -> hbm:rod_zirnox_thorium_fuel | 1 |  | quest Reactors / 00000000E633E1D7 |
| hbm:watz_pellet@0 -> hbm:watz_pellet_schrabidium | 1 |  | quest Reactors / 00000000EA2105C8 |
| hbm:watz_pellet_depleted@1 -> hbm:watz_pellet_depleted_hes | 1 |  | quest Reactors / 00000000ED199F64 |
| hbm:watz_pellet@6 -> hbm:watz_pellet_mep | 1 |  | quest Reactors / 00000000EFB89FC9 |
| hbm:watz_pellet@1 -> hbm:watz_pellet_hes | 1 |  | quest Reactors / 00000000F1DBA39C |
| hbm:watz_pellet_depleted@5 -> hbm:watz_pellet_depleted_meu | 1 |  | quest Reactors / 00000000FCEDAD2A |
| hbm:rod_zirnox@6 -> hbm:rod_zirnox_u233_fuel | 1 |  | quest Reactors / 00000000FE94E273 |
| hbm:conveyor_wand@3 -> hbm:conveyor_wand_triple | 2 |  | chapter Automation |
| hbm:drone@4 -> hbm:drone_request | 1 |  | quest Automation / 000000000085B6AB |
| hbm:conveyor_wand@2 -> hbm:conveyor_wand_double | 1 |  | quest Automation / 0000000011C6C63C |
| hbm:drone@0 -> hbm:drone_patrol | 4 |  | quest Automation / 00000000177E7E3C |
| hbm:conveyor_wand@1 -> hbm:conveyor_wand_express | 1 |  | quest Automation / 00000000EF7069F9 |
| hbm:grenade_filling@6 -> hbm:grenade_filling_emp | 1 |  | quest Grenades / 00000000176D03F8 |
| hbm:grenade_filling@0 -> hbm:grenade_filling_powder | 1 |  | quest Grenades / 0000000028F3CF76 |
| hbm:grenade_extra@1 -> hbm:grenade_extra_proxy_fuze | 2 |  | quest Grenades / 000000002C63774F |
| hbm:grenade_filling@7 -> hbm:grenade_filling_plasma | 1 |  | quest Grenades / 000000002F64BC4B |
| hbm:grenade_filling@2 -> hbm:grenade_filling_demo | 1 |  | quest Grenades / 0000000034BBA350 |
| hbm:grenade_fuze@4 -> hbm:grenade_fuze_airburst | 1 |  | quest Grenades / 00000000357073BB |
| hbm:grenade_filling@4 -> hbm:grenade_filling_wp | 1 |  | quest Grenades / 0000000039F4A181 |
| hbm:grenade_fuze@3 -> hbm:grenade_fuze_impact | 1 |  | quest Grenades / 000000004BA3943E |
| hbm:grenade_filling@12 -> hbm:grenade_filling_schrab | 1 |  | quest Grenades / 000000005720E3A4 |
| hbm:grenade_filling@10 -> hbm:grenade_filling_nuclear | 1 |  | quest Grenades / 0000000065F7DEFD |
| hbm:grenade_filling@11 -> hbm:grenade_filling_nuclear_demo | 1 |  | quest Grenades / 000000006BCDCAC6 |
| hbm:grenade_filling@8 -> hbm:grenade_filling_laser | 1 |  | quest Grenades / 000000007956A711 |
| hbm:grenade_filling@1 -> hbm:grenade_filling_he | 1 |  | quest Grenades / 0000000086C6A655 |
| hbm:grenade_extra@0 -> hbm:grenade_extra_glue | 1 |  | quest Grenades / 000000008ABD860F |
| hbm:grenade_filling@9 -> hbm:grenade_filling_cluster_heavy | 1 |  | quest Grenades / 0000000095FE7730 |
| hbm:grenade_fuze@1 -> hbm:grenade_fuze_s7 | 1 |  | quest Grenades / 00000000B5CF0044 |
| hbm:grenade_shell@3 -> hbm:grenade_shell_nuke | 2 |  | quest Grenades / 00000000B6125646 |
| hbm:grenade_fuze@0 -> hbm:grenade_fuze_s3 | 1 |  | quest Grenades / 00000000C7CC7552 |
| hbm:grenade_filling@5 -> hbm:grenade_filling_cluster | 1 |  | quest Grenades / 00000000D8807C8B |
| hbm:grenade_filling@3 -> hbm:grenade_filling_inc | 1 |  | quest Grenades / 00000000DC7AF944 |
| hbm:grenade_fuze@2 -> hbm:grenade_fuze_s15 | 1 |  | quest Grenades / 00000000E57D5648 |
| hbm:grenade_shell@2 -> hbm:grenade_shell_tech | 2 |  | quest Grenades / 00000000FACBC2CC |
| hbm:grenade_extra@2 -> hbm:grenade_extra_frag_sleeve | 1 |  | quest Grenades / 00000000FD2CFD85 |
| hbm:circuit@0 -> hbm:circuit_vacuum_tube | 1 |  | quest Missiles / 00000000B65079BF |
| hbm:weapon_mod_generic@10 -> hbm:weapon_mod_generic_ferro_damage | 1 |  | quest Weapons / 00000000038C12E8 |
| hbm:weapon_mod_generic@5 -> hbm:weapon_mod_generic_dura_dura | 1 |  | quest Weapons / 0000000004BF0DDE |
| hbm:weapon_mod_generic@6 -> hbm:weapon_mod_generic_desh_damage | 1 |  | quest Weapons / 0000000006E89CB1 |
| hbm:weapon_mod_generic@15 -> hbm:weapon_mod_generic_bigmt_dura | 1 |  | quest Weapons / 00000000083EC886 |
| hbm:weapon_mod_special@27 -> hbm:weapon_mod_special_sifter | 1 |  | quest Weapons / 0000000009575738 |
| hbm:weapon_mod_generic@14 -> hbm:weapon_mod_generic_bigmt_damage | 1 |  | quest Weapons / 000000000B7D9345 |
| hbm:weapon_mod_special@0 -> hbm:weapon_mod_special_silencer | 1 |  | quest Weapons / 000000000B98E194 |
| hbm:weapon_mod_special@28 -> hbm:weapon_mod_special_canisters | 1 |  | quest Weapons / 000000002B18A0F8 |
| hbm:weapon_mod_generic@11 -> hbm:weapon_mod_generic_ferro_dura | 1 |  | quest Weapons / 0000000033FF023A |
| hbm:weapon_mod_generic@0 -> hbm:weapon_mod_generic_iron_damage | 1 |  | quest Weapons / 0000000034B65110 |
| hbm:weapon_mod_generic@1 -> hbm:weapon_mod_generic_iron_dura | 1 |  | quest Weapons / 000000003809F741 |
| hbm:weapon_mod_special@15 -> hbm:weapon_mod_special_las_auto | 1 |  | quest Weapons / 000000003C692214 |
| hbm:weapon_mod_caliber@7 -> hbm:weapon_mod_caliber_bmg50 | 1 |  | quest Weapons / 000000003C73DDC7 |
| hbm:weapon_mod_caliber@6 -> hbm:weapon_mod_caliber_r762 | 1 |  | quest Weapons / 000000004393A806 |
| hbm:weapon_mod_generic@13 -> hbm:weapon_mod_generic_tcalloy_dura | 1 |  | quest Weapons / 00000000454AB994 |
| hbm:weapon_mod_special@22 -> hbm:weapon_mod_special_engine_diesel | 1 |  | quest Weapons / 000000004FCB3557 |
| hbm:weapon_mod_special@1 -> hbm:weapon_mod_special_scope | 1 |  | quest Weapons / 000000005476D4E7 |
| hbm:weapon_mod_special@3 -> hbm:weapon_mod_special_greasegun | 1 |  | quest Weapons / 0000000056F1B168 |
| hbm:weapon_mod_generic@7 -> hbm:weapon_mod_generic_desh_dura | 1 |  | quest Weapons / 000000005F2E86F7 |
| hbm:weapon_mod_special@10 -> hbm:weapon_mod_special_bayonet | 1 |  | quest Weapons / 000000006005032C |
| hbm:weapon_mod_generic@9 -> hbm:weapon_mod_generic_wsteel_dura | 1 |  | quest Weapons / 0000000064CDD01F |
| hbm:weapon_mod_caliber@3 -> hbm:weapon_mod_caliber_m357 | 1 |  | quest Weapons / 0000000067C48F29 |
| hbm:weapon_mod_special@5 -> hbm:weapon_mod_special_speedup | 1 |  | quest Weapons / 0000000068F8E4EB |
| hbm:weapon_mod_special@8 -> hbm:weapon_mod_special_furniture_green | 1 |  | quest Weapons / 000000006AFCAD3A |
| hbm:weapon_mod_generic@8 -> hbm:weapon_mod_generic_wsteel_damage | 1 |  | quest Weapons / 0000000074DB870A |
| hbm:weapon_mod_special@2 -> hbm:weapon_mod_special_saw | 1 |  | quest Weapons / 00000000793E0F8C |
| hbm:weapon_mod_special@6 -> hbm:weapon_mod_special_choke | 1 |  | quest Weapons / 000000007A013F38 |
| hbm:weapon_mod_special@9 -> hbm:weapon_mod_special_furniture_black | 1 |  | quest Weapons / 000000007C3E2909 |
| hbm:weapon_mod_generic@2 -> hbm:weapon_mod_generic_steel_damage | 1 |  | quest Weapons / 00000000950DD92D |
| hbm:weapon_mod_special@13 -> hbm:weapon_mod_special_las_shotgun | 1 |  | quest Weapons / 00000000971605FD |
| hbm:weapon_mod_generic@3 -> hbm:weapon_mod_generic_steel_dura | 1 |  | quest Weapons / 0000000097F23212 |
| hbm:weapon_mod_special@14 -> hbm:weapon_mod_special_las_capacitor | 1 |  | quest Weapons / 000000009DAEB3AF |
| hbm:weapon_mod_special@24 -> hbm:weapon_mod_special_engine_electric | 1 |  | quest Weapons / 000000009DEE8DA6 |
| hbm:weapon_mod_generic@12 -> hbm:weapon_mod_generic_tcalloy_damage | 1 |  | quest Weapons / 00000000A282E521 |
| hbm:weapon_mod_caliber@5 -> hbm:weapon_mod_caliber_r556 | 1 |  | quest Weapons / 00000000B6BF8BD2 |
| hbm:weapon_mod_caliber@1 -> hbm:weapon_mod_caliber_p45 | 1 |  | quest Weapons / 00000000B7C71FD7 |
| hbm:weapon_mod_special@4 -> hbm:weapon_mod_special_slowdown | 1 |  | quest Weapons / 00000000B7F827D6 |
| hbm:weapon_mod_caliber@2 -> hbm:weapon_mod_caliber_p22 | 1 |  | quest Weapons / 00000000D346113B |
| hbm:weapon_mod_special@7 -> hbm:weapon_mod_special_speedloader | 1 |  | quest Weapons / 00000000D5ACFEAA |
| hbm:weapon_mod_generic@17 -> hbm:weapon_mod_generic_bronze_dura | 1 |  | quest Weapons / 00000000D8AA9805 |
| hbm:weapon_mod_special@25 -> hbm:weapon_mod_special_engine_turbo | 1 |  | quest Weapons / 00000000D91C56B9 |
| hbm:weapon_mod_caliber@4 -> hbm:weapon_mod_caliber_m44 | 1 |  | quest Weapons / 00000000DE68B102 |
| hbm:weapon_mod_special@11 -> hbm:weapon_mod_special_stack_mag | 1 |  | quest Weapons / 00000000E8019480 |
| hbm:weapon_mod_generic@4 -> hbm:weapon_mod_generic_dura_damage | 1 |  | quest Weapons / 00000000E8B273FC |
| hbm:weapon_mod_generic@16 -> hbm:weapon_mod_generic_bronze_damage | 1 |  | quest Weapons / 00000000EC75427D |
| hbm:weapon_mod_special@23 -> hbm:weapon_mod_special_engine_aviation | 1 |  | quest Weapons / 00000000F558D2F4 |
| hbm:weapon_mod_special@26 -> hbm:weapon_mod_special_magnet | 1 |  | quest Weapons / 00000000FAECCBEE |
| hbm:weapon_mod_caliber@0 -> hbm:weapon_mod_caliber_p9 | 1 |  | quest Weapons / 00000000FB9BB97E |
| hbm:weapon_mod_special@12 -> hbm:weapon_mod_special_skin_saturnite | 1 |  | quest Weapons / 00000000FED3A7BE |
| hbm:chunk_ore@0 -> hbm:chunk_ore_rare | 3 |  | quest Ores / Rare Earth Ore |
| hbm:ingot_raw@41 -> hbm:ingot_raw_slag | 1 |  | quest Ingots / 0000000041BD8486 |
| hbm:coke@0 -> hbm:coke_coal | 1 |  | reward table Fuel |
| hbm:bedrock_ore_new@32 -> hbm:bedrock_ore_base_washed_light | 1 | name "Washed Light Metal Bedrock Ore" | reward table Bedrock Ore |
| hbm:bedrock_ore_new@33 -> hbm:bedrock_ore_base_washed_heavy | 1 | name "Washed Heavy Metal Bedrock Ore" | reward table Bedrock Ore |
| hbm:bedrock_ore_new@34 -> hbm:bedrock_ore_base_washed_rare | 1 | name "Washed Rare Earth Bedrock Ore" | reward table Bedrock Ore |
| hbm:bedrock_ore_new@35 -> hbm:bedrock_ore_base_washed_actinide | 1 | name "Washed Actinide Bedrock Ore" | reward table Bedrock Ore |
| hbm:bedrock_ore_new@36 -> hbm:bedrock_ore_base_washed_nonmetal | 1 | name "Washed Non-Metal Bedrock Ore" | reward table Bedrock Ore |
| hbm:bedrock_ore_new@37 -> hbm:bedrock_ore_base_washed_crystal | 1 | name "Washed Crystalline Bedrock Ore" | reward table Bedrock Ore |

## derived-renamed (4 distinct, 4 uses)

| from -> to | uses | note | first seen in |
|---|---|---|---|
| hbm:weapon_mod_special@19 -> hbm:weapon_mod_special_drill_weaponsteel | 1 | "Powered Drill - Weapon Steel Drillbit" is now "Powered Drill - Weapon Steel Drill bit" | quest Weapons / 00000000741052B2 |
| hbm:weapon_mod_special@20 -> hbm:weapon_mod_special_drill_tcalloy | 1 | "Powered Drill - Technetium Steel Drillbit" is now "Powered Drill - Technetium Steel Drill bit" | quest Weapons / 00000000AA35EE6B |
| hbm:weapon_mod_special@21 -> hbm:weapon_mod_special_drill_saturnite | 1 | "Powered Drill - Saturnite Drillbit" is now "Powered Drill - Saturnite Drill bit" | quest Weapons / 00000000B8744D2E |
| hbm:weapon_mod_special@18 -> hbm:weapon_mod_special_drill_hss | 1 | "Powered Drill - High-Speed Steel Drillbit" is now "Powered Drill - High-Speed Steel Drill bit" | quest Weapons / 00000000FD648507 |

## component (178 distinct, 379 uses)

| from -> to | uses | note | first seen in |
|---|---|---|---|
| hbm:fluid_icon@82 -> hbm:fluid_icon | 3 | fluid hbm:nitric_acid | quest Early Game / 000000002AD8278A |
| hbm:fluid_tank_full@82 -> hbm:fluid_tank | 1 | fluid hbm:nitric_acid | quest Early Game / 000000002AD8278A |
| hbm:fluid_icon@75 -> hbm:fluid_icon | 3 | fluid hbm:sulfuric_acid | quest Early Game / 000000003121E8F3 |
| hbm:fluid_tank_full@75 -> hbm:fluid_tank | 1 | fluid hbm:sulfuric_acid | quest Early Game / 000000003121E8F3 |
| hbm:fluid_icon@38 -> hbm:fluid_icon | 3 | fluid hbm:hydrogen | quest Early Game / 000000003D998AAB |
| hbm:fluid_tank_full@38 -> hbm:fluid_tank | 1 | fluid hbm:hydrogen | quest Early Game / 000000003D998AAB |
| hbm:fluid_icon@29 -> hbm:fluid_icon | 3 | fluid hbm:uf6 | quest Early Game / 0000000049FAE995 |
| hbm:fluid_tank_full@29 -> hbm:fluid_tank | 1 | fluid hbm:uf6 | quest Early Game / 0000000049FAE995 |
| hbm:fluid_icon@35 -> hbm:fluid_icon | 5 | fluid hbm:peroxide | quest Early Game / 000000007D265608 |
| hbm:fluid_tank_full@35 -> hbm:fluid_tank | 1 | fluid hbm:peroxide | quest Early Game / 000000007D265608 |
| hbm:fluid_icon@42 -> hbm:fluid_icon | 3 | fluid hbm:mercury | quest Early Game / 000000008B065BDC |
| hbm:fluid_tank_full@42 -> hbm:fluid_tank | 2 | fluid hbm:mercury | quest Early Game / 000000008B065BDC |
| hbm:fluid_icon@1 -> hbm:fluid_icon | 4 | fluid minecraft:water | quest Early Game / 00000000F6C76B50 |
| hbm:fluid_tank_full@67 -> hbm:fluid_tank | 2 | fluid hbm:aromatics | quest Oil Age / 000000000477FBC8 |
| hbm:fluid_tank_full@24 -> hbm:fluid_tank | 2 | fluid hbm:petroleum | quest Oil Age / 000000000477FBC8 |
| hbm:fluid_icon@21 -> hbm:fluid_icon | 3 | fluid hbm:lightoil | quest Oil Age / Light Oil |
| hbm:fluid_tank_full@21 -> hbm:fluid_tank | 2 | fluid hbm:lightoil | quest Oil Age / Light Oil |
| hbm:fluid_icon@61 -> hbm:fluid_icon | 3 | fluid hbm:crackoil | quest Oil Age / 000000001F7408EE |
| hbm:fluid_tank_full@61 -> hbm:fluid_tank | 1 | fluid hbm:crackoil | quest Oil Age / 000000001F7408EE |
| hbm:fluid_icon@110 -> hbm:fluid_icon | 3 | fluid hbm:estradiol | quest Oil Age / Gender Swapping Tactics |
| hbm:fluid_icon@65 -> hbm:fluid_icon | 3 | fluid hbm:lightoil_crack | quest Oil Age / 0000000031342DE8 |
| hbm:fluid_icon@12 -> hbm:fluid_icon | 3 | fluid hbm:heavyoil | quest Oil Age / Heavy Oil |
| hbm:fluid_tank_full@12 -> hbm:fluid_tank | 1 | fluid hbm:heavyoil | quest Oil Age / Heavy Oil |
| hbm:fluid_icon@67 -> hbm:fluid_icon | 3 | fluid hbm:aromatics | quest Oil Age / 000000006E938840 |
| hbm:fluid_icon@83 -> hbm:fluid_icon | 3 | fluid hbm:solvent | quest Oil Age / 0000000070C32CB5 |
| hbm:fluid_icon@46 -> hbm:fluid_icon | 4 | fluid hbm:gasoline | quest Oil Age / Gasoline |
| hbm:fluid_tank_full@46 -> hbm:fluid_tank | 1 | fluid hbm:gasoline | quest Oil Age / Gasoline |
| hbm:fluid_icon@11 -> hbm:fluid_icon | 3 | fluid hbm:hotoil | quest Oil Age / 00000000893C81C8 |
| hbm:fluid_tank_full@11 -> hbm:fluid_tank | 1 | fluid hbm:hotoil | quest Oil Age / 00000000893C81C8 |
| hbm:fluid_icon@55 -> hbm:fluid_icon | 3 | fluid hbm:carbondioxide | quest Oil Age / 0000000095DB2E2A |
| hbm:fluid_barrel_full@22 -> hbm:fluid_barrel | 3 | fluid hbm:kerosene | quest Oil Age / 00000000A56D197F |
| hbm:fluid_icon@68 -> hbm:fluid_icon | 3 | fluid hbm:unsaturateds | quest Oil Age / 00000000A5CD0C00 |
| hbm:fluid_tank_full@68 -> hbm:fluid_tank | 2 | fluid hbm:unsaturateds | quest Oil Age / 00000000A5CD0C00 |
| hbm:fluid_icon@64 -> hbm:fluid_icon | 3 | fluid hbm:naphtha_crack | quest Oil Age / 00000000AACEBA97 |
| hbm:fluid_icon@24 -> hbm:fluid_icon | 3 | fluid hbm:petroleum | quest Oil Age / Petroleum Gas |
| hbm:fluid_icon@19 -> hbm:fluid_icon | 3 | fluid hbm:naphtha | quest Oil Age / Naptha |
| hbm:fluid_tank_full@19 -> hbm:fluid_tank | 1 | fluid hbm:naphtha | quest Oil Age / Naptha |
| hbm:fluid_icon@73 -> hbm:fluid_icon | 3 | fluid hbm:gasoline_leaded | quest Oil Age / 00000000F771E019 |
| hbm:fluid_tank_full@73 -> hbm:fluid_tank | 1 | fluid hbm:gasoline_leaded | quest Oil Age / 00000000F771E019 |
| hbm:fluid_icon@23 -> hbm:fluid_icon | 3 | fluid hbm:gas | quest Oil Age / 00000000FD3DB02D |
| hbm:fluid_tank_full@23 -> hbm:fluid_tank | 1 | fluid hbm:gas | quest Oil Age / 00000000FD3DB02D |
| hbm:fluid_icon@142 -> hbm:fluid_icon | 3 | fluid hbm:slop | quest Bedrock Ore / 00000000D6EF4B2A |
| hbm:fluid_tank_full@142 -> hbm:fluid_tank | 1 | fluid hbm:slop | quest Bedrock Ore / 00000000D6EF4B2A |
| hbm:fluid_tank_full@90 -> hbm:fluid_tank | 1 | fluid hbm:chlorine | quest Late Game / 000000000AB4A972 |
| hbm:fluid_icon@131 -> hbm:fluid_icon | 3 | fluid hbm:fullerene | quest Late Game / 000000006289E39E |
| hbm:fluid_icon@95 -> hbm:fluid_icon | 3 | fluid hbm:xylene | quest Late Game / 0000000064C1C604 |
| hbm:fluid_tank_full@95 -> hbm:fluid_tank | 1 | fluid hbm:xylene | quest Late Game / 0000000064C1C604 |
| hbm:fluid_barrel_full@41 -> hbm:fluid_barrel | 1 | fluid hbm:balefire | quest Late Game / 000000006DBB1C9D |
| hbm:fluid_icon@88 -> hbm:fluid_icon | 3 | fluid hbm:radiosolvent | quest Late Game / 000000007057B18B |
| hbm:fluid_tank_full@88 -> hbm:fluid_tank | 1 | fluid hbm:radiosolvent | quest Late Game / 000000007057B18B |
| hbm:fluid_tank_full@41 -> hbm:fluid_tank | 1 | fluid hbm:balefire | quest Late Game / 00000000741702F6 |
| hbm:fluid_icon@92 -> hbm:fluid_icon | 3 | fluid hbm:reformate | quest Late Game / 000000008A71393D |
| hbm:fluid_tank_full@92 -> hbm:fluid_tank | 1 | fluid hbm:reformate | quest Late Game / 000000008A71393D |
| hbm:fluid_icon@100 -> hbm:fluid_icon | 3 | fluid hbm:reformgas | quest Late Game / 000000008A76F5CE |
| hbm:fluid_tank_full@100 -> hbm:fluid_tank | 1 | fluid hbm:reformgas | quest Late Game / 000000008A76F5CE |
| hbm:fluid_icon@60 -> hbm:fluid_icon | 3 | fluid hbm:heavywater | quest Late Game / 00000000D19941B2 |
| hbm:fluid_tank_full@60 -> hbm:fluid_tank | 1 | fluid hbm:heavywater | quest Late Game / 00000000D19941B2 |
| hbm:fluid_barrel_full@95 -> hbm:fluid_barrel | 1 | fluid hbm:xylene | quest Late Game / 00000000E4459CFF |
| hbm:fluid_icon@6 -> hbm:fluid_icon | 3 | fluid hbm:coolant | quest Reactors / 000000001F522B58 |
| hbm:fluid_tank_full@6 -> hbm:fluid_tank | 1 | fluid hbm:coolant | quest Reactors / 000000001F522B58 |
| hbm:fluid_icon@41 -> hbm:fluid_icon | 3 | fluid hbm:balefire | chapter Fluids |
| hbm:fluid_icon@16 -> hbm:fluid_icon | 2 | fluid hbm:reclaimed | quest Fluids / 0000000005426087 |
| hbm:fluid_icon@27 -> hbm:fluid_icon | 2 | fluid hbm:biofuel | quest Fluids / 000000000C43815E |
| hbm:fluid_icon@90 -> hbm:fluid_icon | 2 | fluid hbm:chlorine | quest Fluids / 000000000F2DE7CB |
| hbm:fluid_icon@80 -> hbm:fluid_icon | 2 | fluid hbm:coalcreosote | quest Fluids / 000000000FD7B1F4 |
| hbm:fluid_icon@136 -> hbm:fluid_icon | 2 | fluid hbm:crackoil_ds | quest Fluids / 0000000013319DBE |
| hbm:fluid_icon@40 -> hbm:fluid_icon | 3 | fluid hbm:xenon | quest Fluids / 000000001744DDD2 |
| hbm:fluid_icon@71 -> hbm:fluid_icon | 2 | fluid hbm:enderjuice | quest Fluids / 000000001E6FC9D0 |
| hbm:fluid_icon@14 -> hbm:fluid_icon | 2 | fluid hbm:smear | quest Fluids / 000000001F29EDA1 |
| hbm:fluid_icon@146 -> hbm:fluid_icon | 2 | fluid hbm:perfluoromethyl_cold | quest Fluids / 0000000020B9BF6E |
| hbm:fluid_icon@22 -> hbm:fluid_icon | 4 | fluid hbm:kerosene | quest Fluids / 0000000022563EBC |
| hbm:fluid_icon@101 -> hbm:fluid_icon | 2 | fluid hbm:colloid | quest Fluids / 00000000231EC937 |
| hbm:fluid_icon@59 -> hbm:fluid_icon | 3 | fluid hbm:ethanol | quest Fluids / 00000000247627FD |
| hbm:fluid_icon@10 -> hbm:fluid_icon | 2 | fluid hbm:oil | quest Fluids / 0000000027237CC4 |
| hbm:fluid_icon@2 -> hbm:fluid_icon | 3 | fluid hbm:steam | quest Fluids / 0000000028B87B73 |
| hbm:fluid_icon@18 -> hbm:fluid_icon | 2 | fluid hbm:lubricant | quest Fluids / 000000002981D222 |
| hbm:fluid_icon@34 -> hbm:fluid_icon | 2 | fluid hbm:aschrab | quest Fluids / 000000002DAE2D39 |
| hbm:fluid_icon@45 -> hbm:fluid_icon | 2 | fluid hbm:wastegas | quest Fluids / 000000002FB25D8A |
| hbm:fluid_icon@3 -> hbm:fluid_icon | 2 | fluid hbm:hotsteam | quest Fluids / 00000000339140D0 |
| hbm:fluid_icon@15 -> hbm:fluid_icon | 2 | fluid hbm:heatingoil | quest Fluids / 00000000368D502C |
| hbm:fluid_icon@25 -> hbm:fluid_icon | 2 | fluid hbm:lpg | quest Fluids / 00000000371CE695 |
| hbm:fluid_icon@106 -> hbm:fluid_icon | 2 | fluid hbm:naphtha_coker | quest Fluids / 00000000390351B8 |
| hbm:fluid_icon@155 -> hbm:fluid_icon | 2 | fluid hbm:airblast | quest Fluids / 000000003A023D97 |
| hbm:fluid_icon@145 -> hbm:fluid_icon | 2 | fluid hbm:perfluoromethyl | quest Fluids / 000000003A617883 |
| hbm:fluid_icon@76 -> hbm:fluid_icon | 2 | fluid hbm:coolant_hot | quest Fluids / 000000003CA09226 |
| hbm:fluid_icon@128 -> hbm:fluid_icon | 2 | fluid hbm:thorium_salt | quest Fluids / 000000003D36656D |
| hbm:fluid_icon@48 -> hbm:fluid_icon | 2 | fluid hbm:spentsteam | quest Fluids / 000000003E9BF401 |
| hbm:fluid_icon@97 -> hbm:fluid_icon | 2 | fluid hbm:diesel_reform | quest Fluids / 000000003FE31A89 |
| hbm:fluid_icon@84 -> hbm:fluid_icon | 2 | fluid hbm:blood | quest Fluids / 0000000040C60B9B |
| hbm:fluid_icon@77 -> hbm:fluid_icon | 2 | fluid hbm:mug | quest Fluids / 0000000041594DDA |
| hbm:fluid_icon@147 -> hbm:fluid_icon | 2 | fluid hbm:perfluoromethyl_hot | quest Fluids / 00000000446F171A |
| hbm:fluid_icon@91 -> hbm:fluid_icon | 2 | fluid hbm:heavyoil_vacuum | quest Fluids / 000000004538A731 |
| hbm:fluid_icon@151 -> hbm:fluid_icon | 3 | fluid hbm:alumina | quest Fluids / 0000000048523863 |
| hbm:fluid_icon@86 -> hbm:fluid_icon | 2 | fluid hbm:syngas | quest Fluids / 000000004B0B3791 |
| hbm:fluid_icon@39 -> hbm:fluid_icon | 4 | fluid hbm:oxygen | quest Fluids / 000000004B2DEC22 |
| hbm:fluid_icon@138 -> hbm:fluid_icon | 2 | fluid hbm:naphtha_ds | quest Fluids / 00000000508A92B6 |
| hbm:fluid_icon@124 -> hbm:fluid_icon | 2 | fluid hbm:helium4 | quest Fluids / 0000000051D3D264 |
| hbm:fluid_icon@87 -> hbm:fluid_icon | 2 | fluid hbm:oxyhydrogen | quest Fluids / 000000005291E8F9 |
| hbm:fluid_icon@129 -> hbm:fluid_icon | 2 | fluid hbm:thorium_salt_hot | quest Fluids / 0000000052CC0373 |
| hbm:fluid_icon@93 -> hbm:fluid_icon | 2 | fluid hbm:lightoil_vacuum | quest Fluids / 0000000056E700EB |
| hbm:fluid_icon@89 -> hbm:fluid_icon | 2 | fluid hbm:hydrazine | quest Fluids / 0000000059A6E4D2 |
| hbm:fluid_icon@122 -> hbm:fluid_icon | 2 | fluid hbm:smoke_leaded | quest Fluids / 000000005FBA345A |
| hbm:fluid_icon@120 -> hbm:fluid_icon | 2 | fluid hbm:calcium_solution | quest Fluids / 00000000609071C4 |
| hbm:fluid_icon@8 -> hbm:fluid_icon | 2 | fluid hbm:deuterium | quest Fluids / 00000000650A55C2 |
| hbm:fluid_icon@140 -> hbm:fluid_icon | 2 | fluid hbm:stellar_flux | quest Fluids / 000000006673803B |
| hbm:fluid_icon@26 -> hbm:fluid_icon | 2 | fluid hbm:biogas | quest Fluids / 0000000067A05F1E |
| hbm:fluid_icon@143 -> hbm:fluid_icon | 2 | fluid hbm:lead | quest Fluids / 000000006C82C5ED |
| hbm:fluid_icon@111 -> hbm:fluid_icon | 2 | fluid hbm:fishoil | quest Fluids / 000000006F374B69 |
| hbm:fluid_icon@127 -> hbm:fluid_icon | 2 | fluid hbm:sodium_hot | quest Fluids / 000000007073309A |
| hbm:fluid_icon@153 -> hbm:fluid_icon | 2 | fluid hbm:concrete | quest Fluids / 0000000072B37BB0 |
| hbm:fluid_icon@148 -> hbm:fluid_icon | 2 | fluid hbm:lye | quest Fluids / 0000000073302773 |
| hbm:fluid_icon@133 -> hbm:fluid_icon | 2 | fluid hbm:pheromone_m | quest Fluids / 0000000075B294D5 |
| hbm:fluid_icon@7 -> hbm:fluid_icon | 2 | fluid minecraft:lava | quest Fluids / 00000000771532EC |
| hbm:fluid_icon@105 -> hbm:fluid_icon | 2 | fluid hbm:oil_coker | quest Fluids / 0000000077234949 |
| hbm:fluid_icon@137 -> hbm:fluid_icon | 2 | fluid hbm:hotcrackoil_ds | quest Fluids / 0000000079348422 |
| hbm:fluid_icon@63 -> hbm:fluid_icon | 2 | fluid hbm:hotcrackoil | quest Fluids / 000000007B1704FE |
| hbm:fluid_icon@62 -> hbm:fluid_icon | 2 | fluid hbm:coaloil | quest Fluids / 000000007BDDBFA3 |
| hbm:fluid_icon@94 -> hbm:fluid_icon | 2 | fluid hbm:sourgas | quest Fluids / 000000007D8D4E52 |
| hbm:fluid_icon@70 -> hbm:fluid_icon | 2 | fluid hbm:xpjuice | quest Fluids / 00000000837DEA0A |
| hbm:fluid_icon@58 -> hbm:fluid_icon | 2 | fluid hbm:death | quest Fluids / 0000000085711BA9 |
| hbm:fluid_icon@49 -> hbm:fluid_icon | 2 | fluid hbm:fracksol | quest Fluids / 000000008739A021 |
| hbm:fluid_icon@28 -> hbm:fluid_icon | 2 | fluid hbm:nitan | quest Fluids / 000000008CDA24B5 |
| hbm:fluid_icon@72 -> hbm:fluid_icon | 2 | fluid hbm:petroil_leaded | quest Fluids / 000000009257E8F7 |
| hbm:fluid_icon@78 -> hbm:fluid_icon | 2 | fluid hbm:mug_hot | quest Fluids / 00000000926D4828 |
| hbm:fluid_icon@57 -> hbm:fluid_icon | 2 | fluid hbm:helium3 | quest Fluids / 0000000093456082 |
| hbm:fluid_icon@9 -> hbm:fluid_icon | 2 | fluid hbm:tritium | quest Fluids / 000000009701A546 |
| hbm:fluid_icon@108 -> hbm:fluid_icon | 2 | fluid hbm:egg | quest Fluids / 000000009907E513 |
| hbm:fluid_icon@118 -> hbm:fluid_icon | 2 | fluid hbm:potassium_chloride | quest Fluids / 000000009AF6EB08 |
| hbm:fluid_icon@43 -> hbm:fluid_icon | 2 | fluid hbm:pain | quest Fluids / 000000009C4D0C9C |
| hbm:fluid_icon@47 -> hbm:fluid_icon | 2 | fluid hbm:coalgas | quest Fluids / 000000009CFD5786 |
| hbm:fluid_icon@69 -> hbm:fluid_icon | 2 | fluid hbm:salient | quest Fluids / 000000009D71439D |
| hbm:fluid_icon@32 -> hbm:fluid_icon | 2 | fluid hbm:schrabidic | quest Fluids / 000000009FFCE365 |
| hbm:fluid_icon@149 -> hbm:fluid_icon | 2 | fluid hbm:sodium_aluminate | quest Fluids / 00000000A29D4029 |
| hbm:fluid_icon@96 -> hbm:fluid_icon | 2 | fluid hbm:heatingoil_vacuum | quest Fluids / 00000000A5D3057D |
| hbm:fluid_icon@139 -> hbm:fluid_icon | 2 | fluid hbm:lightoil_ds | quest Fluids / 00000000A75F530A |
| hbm:fluid_icon@44 -> hbm:fluid_icon | 2 | fluid hbm:wastefluid | quest Fluids / 00000000AE751F98 |
| hbm:fluid_icon@134 -> hbm:fluid_icon | 2 | fluid hbm:oil_ds | quest Fluids / 00000000AF034CA3 |
| hbm:fluid_icon@119 -> hbm:fluid_icon | 2 | fluid hbm:calcium_chloride | quest Fluids / 00000000B26AF834 |
| hbm:fluid_icon@66 -> hbm:fluid_icon | 2 | fluid hbm:diesel_crack | quest Fluids / 00000000B3602300 |
| hbm:fluid_icon@109 -> hbm:fluid_icon | 2 | fluid hbm:cholesterol | quest Fluids / 00000000B6D0C9AF |
| hbm:fluid_icon@144 -> hbm:fluid_icon | 2 | fluid hbm:lead_hot | quest Fluids / 00000000B78421A1 |
| hbm:fluid_icon@98 -> hbm:fluid_icon | 2 | fluid hbm:diesel_crack_reform | quest Fluids / 00000000BD4FD5E9 |
| hbm:fluid_icon@115 -> hbm:fluid_icon | 2 | fluid hbm:chlorocalcite_solution | quest Fluids / 00000000C011C24B |
| hbm:fluid_icon@17 -> hbm:fluid_icon | 2 | fluid hbm:petroil | quest Fluids / 00000000C3CF7A76 |
| hbm:fluid_icon@107 -> hbm:fluid_icon | 2 | fluid hbm:gas_coker | quest Fluids / 00000000C42486AC |
| hbm:fluid_icon@113 -> hbm:fluid_icon | 2 | fluid hbm:nitroglycerin | quest Fluids / 00000000C56234D3 |
| hbm:fluid_icon@132 -> hbm:fluid_icon | 2 | fluid hbm:pheromone | quest Fluids / 00000000C616CDC0 |
| hbm:fluid_icon@135 -> hbm:fluid_icon | 2 | fluid hbm:hotoil_ds | quest Fluids / 00000000C862BBD6 |
| hbm:fluid_icon@103 -> hbm:fluid_icon | 2 | fluid hbm:mustardgas | quest Fluids / 00000000C9EF9CB8 |
| hbm:fluid_icon@114 -> hbm:fluid_icon | 2 | fluid hbm:redmud | quest Fluids / 00000000CA9B3791 |
| hbm:fluid_icon@79 -> hbm:fluid_icon | 2 | fluid hbm:woodoil | quest Fluids / 00000000CC3D05D1 |
| hbm:fluid_icon@112 -> hbm:fluid_icon | 2 | fluid hbm:sunfloweroil | quest Fluids / 00000000CE499813 |
| hbm:fluid_icon@81 -> hbm:fluid_icon | 2 | fluid hbm:seedslurry | quest Fluids / 00000000D28AFAD4 |
| hbm:fluid_icon@99 -> hbm:fluid_icon | 3 | fluid hbm:kerosene_reform | quest Fluids / 00000000D31DA8AE |
| hbm:fluid_icon@141 -> hbm:fluid_icon | 2 | fluid hbm:vitriol | quest Fluids / 00000000D3A0D327 |
| hbm:fluid_icon@74 -> hbm:fluid_icon | 2 | fluid hbm:coalgas_leaded | quest Fluids / 00000000D55C1F8A |
| hbm:fluid_icon@116 -> hbm:fluid_icon | 2 | fluid hbm:chlorocalcite_mix | quest Fluids / 00000000D9C3B65F |
| hbm:fluid_icon@31 -> hbm:fluid_icon | 2 | fluid hbm:sas3 | quest Fluids / 00000000DDA2D915 |
| hbm:fluid_icon@123 -> hbm:fluid_icon | 2 | fluid hbm:smoke_poison | quest Fluids / 00000000DF7D2FB8 |
| hbm:fluid_icon@154 -> hbm:fluid_icon | 2 | fluid hbm:dhc | quest Fluids / 00000000DFFBF78A |
| hbm:fluid_icon@117 -> hbm:fluid_icon | 2 | fluid hbm:chlorocalcite_cleaned | quest Fluids / 00000000E154AE97 |
| hbm:fluid_icon@33 -> hbm:fluid_icon | 2 | fluid hbm:amat | quest Fluids / 00000000E1BC879E |
| hbm:fluid_icon@20 -> hbm:fluid_icon | 2 | fluid hbm:diesel | quest Fluids / 00000000E1D7ED8C |
| hbm:fluid_icon@37 -> hbm:fluid_icon | 2 | fluid hbm:cryogel | quest Fluids / 00000000E8D23A34 |
| hbm:fluid_icon@102 -> hbm:fluid_icon | 2 | fluid hbm:phosgene | quest Fluids / 00000000EA002291 |
| hbm:fluid_icon@125 -> hbm:fluid_icon | 2 | fluid hbm:heavywater_hot | quest Fluids / 00000000EA1FD4E1 |
| hbm:fluid_icon@121 -> hbm:fluid_icon | 2 | fluid hbm:smoke | quest Fluids / 00000000EA279A15 |
| hbm:fluid_icon@13 -> hbm:fluid_icon | 2 | fluid hbm:bitumen | quest Fluids / 00000000ECAF0A27 |
| hbm:fluid_icon@36 -> hbm:fluid_icon | 2 | fluid hbm:watz | quest Fluids / 00000000EEDD1610 |
| hbm:fluid_icon@156 -> hbm:fluid_icon | 2 | fluid hbm:flue | quest Fluids / 00000000F0FF7DA0 |
| hbm:fluid_icon@4 -> hbm:fluid_icon | 2 | fluid hbm:superhotsteam | quest Fluids / 00000000F415F731 |
| hbm:fluid_icon@85 -> hbm:fluid_icon | 2 | fluid hbm:blood_hot | quest Fluids / 00000000F7BFD00D |
| hbm:fluid_icon@150 -> hbm:fluid_icon | 2 | fluid hbm:bauxite_solution | quest Fluids / 00000000F7CA067D |
| hbm:fluid_icon@5 -> hbm:fluid_icon | 2 | fluid hbm:ultrahotsteam | quest Fluids / 00000000FA838D1B |
| hbm:fluid_icon@152 -> hbm:fluid_icon | 2 | fluid hbm:air | quest Fluids / 00000000FB616197 |
| hbm:fluid_icon@130 -> hbm:fluid_icon | 2 | fluid hbm:thorium_salt_depleted | quest Fluids / 00000000FDD50990 |
| hbm:fluid_icon@30 -> hbm:fluid_icon | 2 | fluid hbm:puf6 | quest Fluids / 00000000FE72764F |
| hbm:fluid_icon@104 -> hbm:fluid_icon | 2 | fluid hbm:iongel | quest Fluids / 00000000FFD6AC84 |

## mapped (40 distinct, 43 uses)

| from -> to | uses | note | first seen in |
|---|---|---|---|
| image click https://nucleartech.wiki/wiki/Main_Page -> click_action open_uri | 1 | keys read from a file FTB Quests 26.1.2 saved | chapter LvNTM |
| minecraft:spawn_egg minecraft:villager -> minecraft:villager_spawn_egg | 1 | spawn egg of that entity | quest LvNTM / Author |
| minecraft:spawn_egg hbm:entity_elder_one -> hbm:entity_elder_one_spawn_egg | 1 | spawn egg of that entity | quest LvNTM / Author |
| fluid nitric_acid -> hbm:nitric_acid | 1 | fluid id | quest Early Game / 000000002AD8278A |
| fluid sulfuric_acid -> hbm:sulfuric_acid | 1 | fluid id | quest Early Game / 000000003121E8F3 |
| fluid hydrogen -> hbm:hydrogen | 1 | fluid id | quest Early Game / 000000003D998AAB |
| fluid uf6 -> hbm:uf6 | 1 | fluid id | quest Early Game / 0000000049FAE995 |
| fluid peroxide -> hbm:peroxide | 1 | fluid id | quest Early Game / 000000007D265608 |
| fluid mercury -> hbm:mercury | 1 | fluid id | quest Early Game / 000000008B065BDC |
| fluid lightoil -> hbm:lightoil | 1 | fluid id | quest Oil Age / Light Oil |
| fluid crackoil -> hbm:crackoil | 1 | fluid id | quest Oil Age / 000000001F7408EE |
| fluid heavyoil -> hbm:heavyoil | 1 | fluid id | quest Oil Age / Heavy Oil |
| fluid aromatics -> hbm:aromatics | 1 | fluid id | quest Oil Age / 000000006E938840 |
| fluid gasoline -> hbm:gasoline | 1 | fluid id | quest Oil Age / Gasoline |
| fluid hotoil -> hbm:hotoil | 1 | fluid id | quest Oil Age / 00000000893C81C8 |
| fluid unsaturateds -> hbm:unsaturateds | 1 | fluid id | quest Oil Age / 00000000A5CD0C00 |
| fluid petroleum -> hbm:petroleum | 1 | fluid id | quest Oil Age / Petroleum Gas |
| fluid naphtha -> hbm:naphtha | 1 | fluid id | quest Oil Age / Naptha |
| fluid gas -> hbm:gas | 1 | fluid id | quest Oil Age / 00000000FD3DB02D |
| fluid slop -> hbm:slop | 1 | fluid id | quest Bedrock Ore / 00000000D6EF4B2A |
| image click https://www.youtube.com/watch?v=os7E3b8NO64 -> click_action open_uri | 1 | keys read from a file FTB Quests 26.1.2 saved | chapter Late Game |
| image click https://youtu.be/TrAtMS970sI?si=WPKnZb55blCrOdIs -> click_action open_uri | 1 | keys read from a file FTB Quests 26.1.2 saved | chapter Late Game |
| image click https://nucleartech.wiki/wiki/Inertial_Confinement_Fusion_Reactor#Laser_Port -> click_action open_uri | 1 | keys read from a file FTB Quests 26.1.2 saved | chapter Late Game |
| fluid xylene -> hbm:xylene | 1 | fluid id | quest Late Game / 0000000064C1C604 |
| fluid radiosolvent -> hbm:radiosolvent | 1 | fluid id | quest Late Game / 000000007057B18B |
| fluid reformate -> hbm:reformate | 1 | fluid id | quest Late Game / 000000008A71393D |
| fluid reformgas -> hbm:reformgas | 1 | fluid id | quest Late Game / 000000008A76F5CE |
| fluid heavywater -> hbm:heavywater | 1 | fluid id | quest Late Game / 00000000D19941B2 |
| image click https://www.youtube.com/watch?v=tTl2GgvBbag -> click_action open_uri | 1 | keys read from a file FTB Quests 26.1.2 saved | chapter Reactors |
| image click https://www.youtube.com/watch?v=3nHCB3QCysk -> click_action open_uri | 1 | keys read from a file FTB Quests 26.1.2 saved | chapter Reactors |
| fluid coolant -> hbm:coolant | 1 | fluid id | quest Reactors / 000000001F522B58 |
| image click https://refinedmods.com/refined-storage/v3.2.1/index.html -> click_action open_uri | 1 | keys read from a file FTB Quests 26.1.2 saved | chapter Refined Storage |
| hbm:entity_ntm_test_dummy -> hbm:entity_test_dummy | 2 | entity renamed | quest Checklists / 00000000C9D6BE8E |
| minecraft:spawn_egg hbm:entity_ntm_test_dummy -> hbm:entity_test_dummy_spawn_egg | 2 | spawn egg of that entity | quest Checklists / 00000000C9D6BE8E |
| image click https://youtu.be/EaPSZcSy4Sg?si=C98qDnocTOt68Dyb&t=170 -> click_action open_uri | 1 | keys read from a file FTB Quests 26.1.2 saved | chapter Automation |
| hbm:entity_ntm_radiation_blaze -> hbm:entity_radiation_blaze | 1 | entity renamed | quest Bosses / 00000000103472EC |
| hbm:entity_mask_man -> hbm:entity_mob_mask_man | 1 | entity renamed | quest Bosses / 00000000146FE7A6 |
| hbm:entity_nuclear_creeper -> hbm:entity_mob_nuclear_creeper | 2 | entity renamed | quest Bosses / 00000000A23994CF |
| minecraft:spawn_egg hbm:entity_nuclear_creeper -> hbm:entity_mob_nuclear_creeper_spawn_egg | 1 | spawn egg of that entity | quest Bosses / 00000000A23994CF |
| hbm:entity_ntm_ufo -> hbm:entity_ufo | 1 | entity renamed | quest Bosses / 00000000BAF467A7 |

