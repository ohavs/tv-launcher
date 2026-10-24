package com.ohav.tvhome.aerial

/**
 * Aerial clips streamed from Wikimedia Commons (1080p VP9 transcodes) and cached on device.
 * Start/end trim intros, titles and city shots.
 */
data class AerialClip(
    val url: String,
    val place: String,
    val credit: String,
    val startMs: Long,
    val endMs: Long,
)

object Aerials {
    private const val T = "https://upload.wikimedia.org/wikipedia/commons/transcoded"

    const val USER_AGENT = "TVHomeLauncher/1.0 (https://github.com/ohavs/tv-launcher; personal TV screensaver)"

    val clips = listOf(
        AerialClip("$T/7/7b/Drone_flyover_video_of_Myoko-Togakushi_Renzan_National_Park.webm/Drone_flyover_video_of_Myoko-Togakushi_Renzan_National_Park.webm.1080p.vp9.webm",
            "Myoko-Togakushi, Japan", "Benlisquare · CC BY-SA 4.0", 30_000, 150_000),
        AerialClip("$T/7/7b/Drone_flyover_video_of_Myoko-Togakushi_Renzan_National_Park.webm/Drone_flyover_video_of_Myoko-Togakushi_Renzan_National_Park.webm.1080p.vp9.webm",
            "Myoko-Togakushi, Japan", "Benlisquare · CC BY-SA 4.0", 300_000, 420_000),
        AerialClip("$T/8/8a/Drone_Footage_Black_Balsam_Knob_NC_4k.webm/Drone_Footage_Black_Balsam_Knob_NC_4k.webm.1080p.vp9.webm",
            "Black Balsam Knob, North Carolina", "Royalty Free Drone Footage · CC BY 3.0", 5_000, 115_000),
        AerialClip("$T/f/f9/Fall_foliage_colors_in_Virginia_-_4k_Drone_view.webm/Fall_foliage_colors_in_Virginia_-_4k_Drone_view.webm.1080p.vp9.webm",
            "Autumn in Virginia", "Sudeep Gowda · CC BY 3.0", 120_000, 300_000),
        AerialClip("$T/0/04/ISS_4K_Crew_Earth_Observations.webm/ISS_4K_Crew_Earth_Observations.webm.1080p.vp9.webm",
            "Earth from the ISS", "NASA · Public domain", 10_000, 62_000),
        AerialClip("$T/a/a7/Earth_Day_2017_-_4K_Earth_Views_From_Space.webm/Earth_Day_2017_-_4K_Earth_Views_From_Space.webm.1080p.vp9.webm",
            "Earth from the ISS", "NASA Johnson · Public domain", 60_000, 300_000),
        AerialClip("$T/d/de/The_Earth-_4K_Extended_Edition.webm/The_Earth-_4K_Extended_Edition.webm.1080p.vp9.webm",
            "Earth from the ISS", "NASA Johnson · Public domain", 600_000, 780_000),
        AerialClip("$T/d/de/The_Earth-_4K_Extended_Edition.webm/The_Earth-_4K_Extended_Edition.webm.1080p.vp9.webm",
            "Earth from the ISS", "NASA Johnson · Public domain", 2_000_000, 2_180_000),
    )
}
