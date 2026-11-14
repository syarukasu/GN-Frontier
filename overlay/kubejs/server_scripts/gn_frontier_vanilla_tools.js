// Vanilla material tools are exploration loot, not a crafting progression.
// This also removes smithing/other-mod recipes that output the same equipment.
ServerEvents.recipes(event => {
  ['iron', 'golden', 'diamond', 'netherite'].forEach(material => {
    ['pickaxe', 'axe', 'shovel', 'hoe', 'sword'].forEach(tool => {
      event.remove({ output: 'minecraft:' + material + '_' + tool });
    });
  });
});
