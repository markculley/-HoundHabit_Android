package com.cometncloud.houndhabit.core.services

import com.cometncloud.houndhabit.core.SupabaseClient
import com.cometncloud.houndhabit.core.models.Distance
import com.cometncloud.houndhabit.core.models.Distraction
import com.cometncloud.houndhabit.core.models.TrainingDuration
import com.cometncloud.houndhabit.core.models.TrainingRecord
import com.cometncloud.houndhabit.core.models.TrainingStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class TrainingRecordService {
    private val supabase get() = SupabaseClient.client

    suspend fun fetchRecords(guardianId: String, petId: String? = null): List<TrainingRecord> =
        supabase.postgrest
            .from("training_records")
            .select {
                filter {
                    eq("guardian_id", guardianId)
                    if (petId != null) eq("pet_id", petId)
                }
                order("recorded_at", Order.DESCENDING)
            }
            .decodeList()

    suspend fun createRecord(
        petId: String,
        guardianId: String,
        recordedAt: Instant,
        score: Int,
        distance: Distance,
        distraction: Distraction,
        duration: TrainingDuration,
        distanceCustom: String? = null,
        durationCustom: String? = null,
        distractionCustom: String? = null,
        notes: String?,
        isShared: Boolean,
        planItemId: String? = null,
    ): TrainingRecord {
        val payload = TrainingRecordInsert(
            petId = petId,
            guardianId = guardianId,
            recordedAt = recordedAt,
            status = TrainingStatus.fromScore(score),
            distance = distance,
            distraction = distraction,
            duration = duration,
            distanceCustom = distanceCustom,
            durationCustom = durationCustom,
            distractionCustom = distractionCustom,
            notes = notes,
            isShared = isShared,
            score = score,
            planItemId = planItemId,
        )
        return supabase.postgrest
            .from("training_records")
            .insert(payload) { select() }
            .decodeSingle()
    }

    suspend fun updateRecord(record: TrainingRecord): TrainingRecord {
        val payload = TrainingRecordUpdate(
            recordedAt = record.recordedAt,
            status = TrainingStatus.fromScore(record.score),
            distance = record.distance,
            distraction = record.distraction,
            duration = record.duration,
            distanceCustom = record.distanceCustom,
            durationCustom = record.durationCustom,
            distractionCustom = record.distractionCustom,
            notes = record.notes,
            isShared = record.isShared,
            score = record.score,
            planItemId = record.planItemId,
        )
        return supabase.postgrest
            .from("training_records")
            .update(payload) {
                select()
                filter { eq("id", record.id) }
            }
            .decodeSingle()
    }

    suspend fun deleteRecord(id: String) {
        supabase.postgrest
            .from("training_records")
            .delete { filter { eq("id", id) } }
    }
}

@Serializable
private data class TrainingRecordInsert(
    @SerialName("pet_id") val petId: String,
    @SerialName("guardian_id") val guardianId: String,
    @SerialName("recorded_at") val recordedAt: Instant,
    val status: TrainingStatus,
    val distance: Distance,
    val distraction: Distraction,
    val duration: TrainingDuration,
    @SerialName("distance_custom") val distanceCustom: String?,
    @SerialName("duration_custom") val durationCustom: String?,
    @SerialName("distraction_custom") val distractionCustom: String?,
    val notes: String?,
    @SerialName("is_shared") val isShared: Boolean,
    val score: Int,
    @SerialName("plan_item_id") val planItemId: String?,
)

@Serializable
private data class TrainingRecordUpdate(
    @SerialName("recorded_at") val recordedAt: Instant,
    val status: TrainingStatus,
    val distance: Distance,
    val distraction: Distraction,
    val duration: TrainingDuration,
    @SerialName("distance_custom") val distanceCustom: String?,
    @SerialName("duration_custom") val durationCustom: String?,
    @SerialName("distraction_custom") val distractionCustom: String?,
    val notes: String?,
    @SerialName("is_shared") val isShared: Boolean,
    val score: Int,
    @SerialName("plan_item_id") val planItemId: String?,
)
