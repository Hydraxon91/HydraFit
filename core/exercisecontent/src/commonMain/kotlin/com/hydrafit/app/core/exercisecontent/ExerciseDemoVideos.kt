package com.hydrafit.app.core.exercisecontent

/**
 * Hand-picked demo videos, keyed by stable catalog exercise ID. Each value is a YouTube video ID;
 * the link is built in [ExerciseDemoLink]. An exercise with no entry falls back to the YouTube
 * search, and custom exercises always do. Entries were chosen by the app's author, mostly from one
 * channel (Andrew Kwong, DeltaBolic) for a consistent style; the rest are single-exercise picks.
 */
internal object ExerciseDemoVideos {
    val videoIdByExerciseId: Map<String, String> = mapOf(
        "barbell-bench-press" to "Ni0aTWvWxCs",
        "close-grip-bench-press" to "43rg7fBNP2w",
        "overhead-press" to "kAhSU3yWFJo",
        "barbell-shoulder-press" to "x_LEzYJFQg4",
        "incline-barbell-press" to "98HWfiRonkE",
        "dumbbell-bench-press" to "mTaiQemkEpU",
        "incline-dumbbell-press" to "8fXfwG4ftaQ",
        "dumbbell-shoulder-press" to "k6tzKisR3NY",
        "back-squat" to "dW3zj79xfrc",
        "front-squat" to "_qv0m3tPd3s",
        "trap-bar-deadlift" to "v-SrIcAp3vM",
        "conventional-deadlift" to "xNwpvDuZJ3k",
        "sumo-deadlift" to "k_jHUVBU-T0",
        "romanian-deadlift" to "5rIqP63yWFg",
        "dumbbell-lunge" to "mJilHWIBWO8",
        "barbell-lunge" to "yNSXHJKYU-s",
        "leg-press" to "EotSw18oR9w",
        "leg-extension" to "uM86QE59Tgc",
        "hip-thrust" to "pUdIL5x0fWg",
        "barbell-hip-thrust" to "pUdIL5x0fWg",
        "pull-up" to "eDP_OOhMTZ4",
        "close-grip-pulldown" to "lYCgyaGpoDk",
        "lat-pulldown" to "bNmvKpJSWKM",
        "wide-grip-pulldown-behind-the-neck" to "SPG4Tfua4bw",
        "barbell-row" to "phVtqawIgbk",
        "seated-cable-row" to "qD1WZ5pSuvk",
        "reverse-machine-fly" to "H5UxZFl0lgk",
        "lying-leg-curls" to "y7SKfM6Hmiw",
        "seated-calf-raise" to "pHm6LFuGGbs",
        "standing-barbell-calf-raise" to "0x-5gTONAWQ",
        "standing-dumbbell-calf-raise" to "hPA98_r-6e4",
        "standing-calf-raise" to "hPA98_r-6e4",
        "ez-bar-skullcrusher" to "K3mFeNz4e3w",
        "dumbbell-triceps-overhead-extension" to "b_r_LW4HEcM",
        "triceps-pushdown" to "-PqzEk57xiw",
        "barbell-curl" to "ez3YoWf62Eg",
        "lateral-raise" to "Kl3LEzQ5Zqs",
        "face-pull" to "IeOqdw9WI90",
        "dumbbell-front-to-lateral-raise-combo" to "SqTLytmDy_8",
        "ez-bar-upright-row" to "pNTPVN3FkSo",
        "ab-roll" to "0vNaXlc3TRc",
        "plank" to "xe2MXatLTUw",
        "cable-crunch" to "dkGwcfo9zto",
        "hanging-leg-raise" to "PpZxg66ftYc",
        "standing-dumbbell-side-bend" to "wY9nQ-yfRwo"
    )
}
