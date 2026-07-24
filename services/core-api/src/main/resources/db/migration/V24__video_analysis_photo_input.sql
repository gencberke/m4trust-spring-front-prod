ALTER TABLE fulfillment_video_analysis_job DROP CONSTRAINT fulfillment_video_analysis_job_media_ck;
ALTER TABLE fulfillment_video_analysis_job ADD CONSTRAINT fulfillment_video_analysis_job_media_ck
    CHECK (input_media_type IN ('video/mp4','image/jpeg','image/png'));
