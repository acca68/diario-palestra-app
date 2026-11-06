package tech.mmarca.openvitals.features.gym

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.LocalTime
import tech.mmarca.openvitals.R
import tech.mmarca.openvitals.domain.model.BodyMeasurementType

/** Diario Palestra: what a body-composition scale report holds, as on the BodyPedia report. */
enum class BodyFieldGroup { COMPOSITION, CIRCUMFERENCE, SEGMENTAL }

/**
 * One line of the report. [goodUp] says which way is progress (null: neither);
 * [healthConnect] is the Health Connect type the value is also written as.
 */
enum class BodyField(
    @param:StringRes val labelRes: Int,
    val unit: String,
    val group: BodyFieldGroup,
    val goodUp: Boolean?,
    val healthConnect: BodyMeasurementType? = null,
) {
    WEIGHT(R.string.body_field_weight, "kg", BodyFieldGroup.COMPOSITION, false, BodyMeasurementType.WEIGHT),
    FAT_PERCENT(R.string.body_field_fat_percent, "%", BodyFieldGroup.COMPOSITION, false, BodyMeasurementType.BODY_FAT),
    FAT_MASS(R.string.body_field_fat_mass, "kg", BodyFieldGroup.COMPOSITION, false),
    LEAN_MASS(R.string.body_field_lean_mass, "kg", BodyFieldGroup.COMPOSITION, true, BodyMeasurementType.LEAN_MASS),
    MUSCLE_MASS(R.string.body_field_muscle_mass, "kg", BodyFieldGroup.COMPOSITION, true),
    SKELETAL_MUSCLE(R.string.body_field_skeletal_muscle, "kg", BodyFieldGroup.COMPOSITION, true),
    WATER(R.string.body_field_water, "kg", BodyFieldGroup.COMPOSITION, null, BodyMeasurementType.BODY_WATER_MASS),
    PROTEIN(R.string.body_field_protein, "kg", BodyFieldGroup.COMPOSITION, true),
    MINERALS(R.string.body_field_minerals, "kg", BodyFieldGroup.COMPOSITION, null),
    BONE(R.string.body_field_bone, "kg", BodyFieldGroup.COMPOSITION, null, BodyMeasurementType.BONE_MASS),
    VISCERAL(R.string.body_field_visceral, "", BodyFieldGroup.COMPOSITION, false),
    BMR(R.string.body_field_bmr, "kcal", BodyFieldGroup.COMPOSITION, true, BodyMeasurementType.BMR),
    BMI(R.string.body_field_bmi, "", BodyFieldGroup.COMPOSITION, false),
    WHR(R.string.body_field_whr, "", BodyFieldGroup.COMPOSITION, false),
    SCORE(R.string.body_field_score, "", BodyFieldGroup.COMPOSITION, true),

    NECK(R.string.body_field_neck, "cm", BodyFieldGroup.CIRCUMFERENCE, null),
    CHEST(R.string.body_field_chest, "cm", BodyFieldGroup.CIRCUMFERENCE, null),
    WAIST(R.string.body_field_waist, "cm", BodyFieldGroup.CIRCUMFERENCE, false),
    HIP(R.string.body_field_hip, "cm", BodyFieldGroup.CIRCUMFERENCE, false),
    ARM_RIGHT(R.string.body_field_arm_right, "cm", BodyFieldGroup.CIRCUMFERENCE, null),
    ARM_LEFT(R.string.body_field_arm_left, "cm", BodyFieldGroup.CIRCUMFERENCE, null),
    THIGH_RIGHT(R.string.body_field_thigh_right, "cm", BodyFieldGroup.CIRCUMFERENCE, null),
    THIGH_LEFT(R.string.body_field_thigh_left, "cm", BodyFieldGroup.CIRCUMFERENCE, null),

    SEG_ARM_RIGHT(R.string.body_field_arm_right, "kg", BodyFieldGroup.SEGMENTAL, true),
    SEG_ARM_LEFT(R.string.body_field_arm_left, "kg", BodyFieldGroup.SEGMENTAL, true),
    SEG_LEG_RIGHT(R.string.body_field_leg_right, "kg", BodyFieldGroup.SEGMENTAL, true),
    SEG_LEG_LEFT(R.string.body_field_leg_left, "kg", BodyFieldGroup.SEGMENTAL, true),
    SEG_TRUNK(R.string.body_field_trunk, "kg", BodyFieldGroup.SEGMENTAL, true),
}

@Immutable
data class BodyReport(
    val date: LocalDate,
    val time: LocalTime? = null,
    val values: Map<BodyField, Double>,
    /** Health Connect record ids written for this report, so an edit updates them instead of duplicating. */
    val healthConnectIds: Map<BodyMeasurementType, String> = emptyMap(),
) {
    operator fun get(field: BodyField): Double? = values[field]
}

@Immutable
data class BodyChange(val value: Double, val previous: Double?, val previousDate: LocalDate?) {
    val delta: Double? get() = previous?.let { value - it }
}

object BodyReports {
    /** The latest value of [field] and the one before it, each from the report that has it. */
    fun change(reports: List<BodyReport>, field: BodyField): BodyChange? {
        val withField = reports.filter { it[field] != null }.sortedBy { it.date }
        val last = withField.lastOrNull() ?: return null
        val previous = withField.getOrNull(withField.size - 2)
        return BodyChange(last[field]!!, previous?.get(field), previous?.date)
    }

    fun series(reports: List<BodyReport>, field: BodyField): List<Pair<LocalDate, Double>> =
        reports.mapNotNull { report -> report[field]?.let { report.date to it } }.sortedBy { it.first }
}
