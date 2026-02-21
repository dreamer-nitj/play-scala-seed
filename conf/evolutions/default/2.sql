# --- !Ups

ALTER TABLE "users" ADD COLUMN "google_id" VARCHAR(255);
ALTER TABLE "users" ADD COLUMN "google_access_token" VARCHAR(2048);
ALTER TABLE "users" ADD COLUMN "google_refresh_token" VARCHAR(2048);
ALTER TABLE "users" ADD COLUMN "google_token_expires_at" BIGINT;
ALTER TABLE "users" ADD COLUMN "email" VARCHAR(255) UNIQUE;
ALTER TABLE "users" ADD COLUMN "email_verified" BOOLEAN DEFAULT FALSE;
ALTER TABLE "users" ADD COLUMN "picture_url" VARCHAR(500);
ALTER TABLE "users" ADD COLUMN "oauth_linked_at" TIMESTAMP;

CREATE INDEX "idx_users_google_id" ON "users"("google_id");
CREATE INDEX "idx_users_email" ON "users"("email");

# --- !Downs

ALTER TABLE "users" DROP COLUMN "google_id";
ALTER TABLE "users" DROP COLUMN "google_access_token";
ALTER TABLE "users" DROP COLUMN "google_refresh_token";
ALTER TABLE "users" DROP COLUMN "google_token_expires_at";
ALTER TABLE "users" DROP COLUMN "email";
ALTER TABLE "users" DROP COLUMN "email_verified";
ALTER TABLE "users" DROP COLUMN "picture_url";
ALTER TABLE "users" DROP COLUMN "oauth_linked_at";

DROP INDEX "idx_users_google_id";
DROP INDEX "idx_users_email";