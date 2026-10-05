package com.feloguarin.lensguard;

/** Common places to examine. Guidance only: the checklist does not detect anything itself. */
final class Checklist {
    static final class Spot {
        final String id;
        final int title;
        final int hint;
        /** Objects that should not contain electronics, where a magnetic change is informative. */
        final boolean magnetic;
        /** Network equipment, best checked with a nearby scan. */
        final boolean nearby;

        Spot(String id, int title, int hint, boolean magnetic, boolean nearby) {
            this.id = id;
            this.title = title;
            this.hint = hint;
            this.magnetic = magnetic;
            this.nearby = nearby;
        }
    }

    static final Spot[] SPOTS = {
            new Spot("smoke_detector", R.string.spot_smoke_detector, R.string.spot_smoke_detector_hint, false, false),
            new Spot("clock_speaker", R.string.spot_clock_speaker, R.string.spot_clock_speaker_hint, false, false),
            new Spot("power", R.string.spot_power, R.string.spot_power_hint, false, false),
            new Spot("tv_media", R.string.spot_tv_media, R.string.spot_tv_media_hint, false, true),
            new Spot("frames_mirrors", R.string.spot_frames_mirrors, R.string.spot_frames_mirrors_hint, true, false),
            new Spot("lamps", R.string.spot_lamps, R.string.spot_lamps_hint, false, false),
            new Spot("shelves_decor", R.string.spot_shelves_decor, R.string.spot_shelves_decor_hint, true, false),
            new Spot("appliances", R.string.spot_appliances, R.string.spot_appliances_hint, false, false),
            new Spot("vents", R.string.spot_vents, R.string.spot_vents_hint, false, false),
            new Spot("bathroom", R.string.spot_bathroom, R.string.spot_bathroom_hint, false, false),
            new Spot("small_items", R.string.spot_small_items, R.string.spot_small_items_hint, true, false),
            new Spot("network", R.string.spot_network, R.string.spot_network_hint, false, true),
    };

    private Checklist() {}

    static Spot find(String id) {
        if (id == null) return null;
        for (Spot spot : SPOTS) if (spot.id.equals(id)) return spot;
        return null;
    }
}
