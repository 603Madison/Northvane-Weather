package app.northvane.core.outlook

import app.northvane.core.model.FoliageReport
import app.northvane.core.model.MudReason
import app.northvane.core.model.Tip
import app.northvane.core.model.Tip.Kind
import app.northvane.core.model.UnitSystem
import app.northvane.core.util.UnitFormatter
import kotlin.math.roundToInt

/** User-facing wording for outlook tips and reasons, in the user's units. */
object OutlookText {

    fun tip(t: Tip, f: UnitFormatter): String {
        val v = t.value
        val temp = { f.temperatureWithUnit(v) }
        val pct = { "${v?.roundToInt() ?: "--"}%" }
        val imperial = f.system == UnitSystem.IMPERIAL
        return when (t.kind) {
            Kind.IDEAL -> "Close to ideal conditions"
            Kind.WARNING -> "A weather warning is in effect: postpone if you can"
            Kind.HOT -> "Hot: feels like ${temp()}"
            Kind.HUMID -> "Muggy: dew point ${temp()}"
            Kind.COLD -> "Cold: feels like ${temp()}"
            Kind.VERY_COLD -> "Bitter cold: feels like ${temp()}. Frostbite risk on exposed skin"
            Kind.RAIN -> "${pct()} chance of rain"
            Kind.SNOW -> if (v != null && v > 0) "Snow: about ${f.snow(v)}" else "Snow on the ground"
            Kind.ICE -> "Freezing rain: icy surfaces"
            Kind.THUNDER -> "${pct()} chance of thunderstorms"
            Kind.WINDY -> "Gusts to ${f.windSpeed(v)}"
            Kind.CLOUDY -> if (v != null) "Cloud cover around ${pct()}" else "Mostly cloudy: limited views"
            Kind.MUDDY -> "Trails will be muddy"
            Kind.MUD_SEASON -> "Mud season: skip high-elevation trails until they dry, and walk through mud, not around it"
            Kind.ROUGH_WATER -> "Choppy water: wind around ${f.windSpeed(v)}"
            Kind.COLD_WATER -> "Cold water: wear your PFD and dress for immersion, not the air temperature"
            Kind.BRIGHT_MOON -> "Bright moon (${pct()} lit) washes out faint stars"
            Kind.SLEEPING_BAG_0F -> "Low near ${temp()}: bring a 0°F (-18°C) bag, an insulated pad (R5+) and keep your water filter and batteries in your bag"
            Kind.SLEEPING_BAG_20F -> "Low near ${temp()}: bring a 20°F (-7°C) bag and an insulated pad (R4+). Don't let your water filter freeze"
            Kind.SLEEPING_BAG_30F -> "Low near ${temp()}: a 30°F (-1°C) bag and a warm layer for the evening"
            Kind.SLEEPING_BAG_SUMMER -> "Low near ${temp()}: a light bag or quilt will do"
            Kind.HOT_NIGHT -> "Warm night (low ${temp()}): pick a breezy site and open the tent vents"
            Kind.RAINFLY -> "Pitch the rainfly, pick high ground, and pack a tarp for camp"
            Kind.GUYLINES -> "Stake out guylines and pick a site sheltered from the wind"
            Kind.CONDENSATION -> "Damp, calm air: expect heavy dew and tent condensation, so ventilate"
            Kind.FROST -> "Frost likely by morning"
            Kind.SUMMIT_WIND -> "Expect much stronger wind and colder air above treeline"
            Kind.EARLY_START -> "Start early and be off exposed ridges and summits by early afternoon"
            Kind.MICROSPIKES -> "Icy spots likely: carry microspikes"
            Kind.SNOWSHOES -> "Snow on the trails: bring snowshoes or traction"
            Kind.LAYERS -> "Dress in layers: hat, gloves and an insulating jacket"
            Kind.HYDRATE -> "Carry extra water and take breaks in the shade"
            Kind.SUNSCREEN -> "Strong sun: sunscreen and a hat"
            Kind.RECOVERY_GEAR -> "Bring recovery gear (strap, shovel, traction boards) and ride with a buddy"
            Kind.AVOID_RUTS -> "Soft trails rut easily: stay off them to avoid damage and closures"
            Kind.TRAIL_CLOSURES -> "Many ATV and bike trails close during mud season. Check before you go"
            Kind.LET_TRAILS_DRY -> "Give trails a day or two to dry after rain: riding wet trails damages them"
            Kind.DUSTY -> "Dry and dusty: keep your distance from the vehicle ahead"
            Kind.BLAZE_ORANGE -> "Hunting season: wear blaze orange"
            Kind.BLACK_FLIES -> "Black fly season: bring a head net and repellent"
            Kind.TICKS -> "Ticks are active: treat clothing, and check yourself afterwards"
            Kind.MOSQUITOES -> "Warm, humid night: mosquitoes, so bring a bug net"
            Kind.SHORT_DAYLIGHT -> "Only ${"%.1f".format(v ?: 0.0)} h of daylight: start early and carry a headlamp"
            Kind.DARK_SKIES -> "Dark, moonless skies: great for the Milky Way"
            Kind.MOONLIT -> "Moon ${pct()} lit: fine for bright objects and planets"
            Kind.OFFSHORE_WIND -> "Stay near shore and watch for offshore wind on bigger lakes and the coast"
            Kind.DRY_BAG -> "Pack a dry bag and rain shell"
            Kind.ICE_ON_WATER -> "Too cold for too long: lakes may be iced over and rivers can carry dangerous ice. Stay off the water"
            Kind.SHORE_ICE -> "Skim ice likely in coves and along shores"
            Kind.ALLERGY_WINDOWS -> "Keep windows closed and run AC or a HEPA filter"
            Kind.ALLERGY_SHOWER -> "Shower and change clothes after time outside"
            Kind.ALLERGY_MEDICATE -> "Take allergy medicine before symptoms start"
            Kind.ALLERGY_TIMING -> "Pollen peaks late morning to afternoon: head out early or after rain"
            Kind.ALLERGY_RAIN_HELPS -> "Steady rain will wash pollen out of the air"
            Kind.ALLERGY_WIND -> "Dry, gusty wind (to ${f.windSpeed(v)}) will spread pollen"
            Kind.ALLERGY_MASK_YARDWORK -> "Damp leaf litter: wear a mask for raking and yard work"
            Kind.ALLERGY_THUNDERSTORM -> "Thunderstorms with high pollen can trigger asthma: keep your inhaler handy"
            Kind.ALLERGY_AIR -> "Air quality is unhealthy for sensitive groups (AQI ${v?.roundToInt()}): limit hard outdoor exertion"
            Kind.ALLERGY_QUIET -> "A quiet day for your allergies"
        }.let { if (imperial) it else metricize(it) }
    }

    /** Drop the Fahrenheit-first gear ratings for metric users. */
    private fun metricize(s: String) = s
        .replace("0°F (-18°C)", "-18°C")
        .replace("20°F (-7°C)", "-7°C")
        .replace("30°F (-1°C)", "-1°C")

    fun mudReason(r: MudReason, f: UnitFormatter): String = when (r.kind) {
        MudReason.Kind.RECENT_RAIN -> "${f.precipitation(r.value)} of rain in the last few days"
        MudReason.Kind.FORECAST_RAIN -> "${f.precipitation(r.value)} of rain expected"
        MudReason.Kind.THAW -> "Spring thaw: the ground is thawing from the top while still frozen below, so water can't drain"
        MudReason.Kind.SNOWMELT -> "Melting snow"
        MudReason.Kind.FROZEN -> "Ground is frozen"
        MudReason.Kind.DRYING -> "Dry weather is firming trails up"
        MudReason.Kind.SNOW_COVER -> "Snow on the ground (about ${f.snow(r.value)})"
        MudReason.Kind.NO_HISTORY -> "Estimated without recent rainfall data"
    }

    fun foliageNote(n: FoliageReport.Note): String = when (n) {
        FoliageReport.Note.COOL_NIGHTS -> "Cool nights this fall are bringing color early"
        FoliageReport.Note.WARM_FALL -> "A warm fall is delaying color"
        FoliageReport.Note.HARD_FREEZE -> "A hard freeze hurried leaves along"
        FoliageReport.Note.DRY -> "Dry weather may mute colors and drop leaves early"
        FoliageReport.Note.HIGH_ELEVATION -> "Higher elevations color and drop earlier than the valleys"
        FoliageReport.Note.NO_HISTORY -> "Based on typical timing only (recent temperatures unavailable)"
    }

    /** Pollen index (Universal Pollen Index 0–5) as words. */
    fun pollenCategory(index: Int?): String = when (index) {
        null -> "Out of season"
        0 -> "None"
        1 -> "Very low"
        2 -> "Low"
        3 -> "Moderate"
        4 -> "High"
        else -> "Very high"
    }
}
