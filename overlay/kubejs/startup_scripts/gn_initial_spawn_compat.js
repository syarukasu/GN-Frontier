// GN r12: preserve the pack's overworld start and spawn protection.
// Anomaly Zone 2.1's welcome handler searches 48 chunks and puts a Valhalla
// portal at the nearest red forest. If the spawn is already in that biome,
// the portal appears directly on the arriving player and forces Adventure.
// Consume only that one-time welcome flag before its NORMAL login listener.
// Existing portals, voluntary travel and the other persisted tags are retained.
if (Platform.isLoaded('anomalyzone') && !global.gnInitialSpawnCompatRegistered) {
  var GNSpawnForge = Java.loadClass('net.minecraftforge.common.MinecraftForge')
  var GNSpawnPriority = Java.loadClass('net.minecraftforge.eventbus.api.EventPriority')
  var GNSpawnLogin = Java.loadClass('net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent')
  GNSpawnForge.EVENT_BUS.addListener(GNSpawnPriority.HIGHEST, false, GNSpawnLogin, event => {
    var data = event.entity.persistentData
    var persisted = data.getCompound('PlayerPersisted')
    if (!persisted.getBoolean('anomalyzone_valhalla_welcome_given')) {
      persisted.putBoolean('anomalyzone_valhalla_welcome_given', true)
      data.put('PlayerPersisted', persisted)
    }
  })
  global.gnInitialSpawnCompatRegistered = true
}
