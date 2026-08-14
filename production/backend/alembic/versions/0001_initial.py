"""Create the v1 production schema.

Revision ID: 0001_initial
Revises:
Create Date: 2026-07-26
"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "0001_initial"
down_revision: str | Sequence[str] | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "users",
        sa.Column("id", sa.String(length=64), nullable=False),
        sa.Column("phone_hash", sa.String(length=64), nullable=False),
        sa.Column("phone_encrypted", sa.Text(), nullable=False),
        sa.Column("plan", sa.String(length=32), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_users_phone_hash", "users", ["phone_hash"], unique=True)
    op.create_table(
        "devices",
        sa.Column("id", sa.String(length=64), nullable=False),
        sa.Column("user_id", sa.String(length=64), nullable=True),
        sa.Column("platform", sa.String(length=16), nullable=False),
        sa.Column("app_version", sa.String(length=32), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("last_seen_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="SET NULL"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_devices_user_id", "devices", ["user_id"], unique=False)
    op.create_table(
        "history_records",
        sa.Column("id", sa.String(length=64), nullable=False),
        sa.Column("subject", sa.String(length=64), nullable=False),
        sa.Column("encrypted_payload", sa.Text(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_history_records_subject", "history_records", ["subject"], unique=False)
    op.create_index(
        "ix_history_records_created_at",
        "history_records",
        ["created_at"],
        unique=False,
    )
    op.create_index(
        "ix_history_records_expires_at",
        "history_records",
        ["expires_at"],
        unique=False,
    )
    op.create_table(
        "client_settings",
        sa.Column("subject", sa.String(length=64), nullable=False),
        sa.Column("theme", sa.String(length=16), nullable=False),
        sa.Column("auto_suggest", sa.Boolean(), nullable=False),
        sa.Column("save_history", sa.Boolean(), nullable=False),
        sa.Column("diagnostics", sa.Boolean(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("subject"),
    )
    op.create_table(
        "refresh_sessions",
        sa.Column("jti", sa.String(length=64), nullable=False),
        sa.Column("subject", sa.String(length=64), nullable=False),
        sa.Column("subject_type", sa.String(length=16), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("consumed_at", sa.DateTime(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint("jti"),
    )
    op.create_index(
        "ix_refresh_sessions_subject",
        "refresh_sessions",
        ["subject"],
        unique=False,
    )
    op.create_index(
        "ix_refresh_sessions_expires_at",
        "refresh_sessions",
        ["expires_at"],
        unique=False,
    )
    op.create_table(
        "sms_challenges",
        sa.Column("phone_hash", sa.String(length=64), nullable=False),
        sa.Column("code_digest", sa.String(length=64), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("next_send_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("failed_attempts", sa.Integer(), nullable=False),
        sa.Column("daily_sent_count", sa.Integer(), nullable=False),
        sa.Column("day_bucket", sa.String(length=10), nullable=False),
        sa.PrimaryKeyConstraint("phone_hash"),
    )
    op.create_table(
        "usage_events",
        sa.Column("id", sa.String(length=64), nullable=False),
        sa.Column("subject", sa.String(length=64), nullable=False),
        sa.Column("request_id", sa.String(length=64), nullable=False),
        sa.Column("model", sa.String(length=64), nullable=True),
        sa.Column("status", sa.String(length=32), nullable=False),
        sa.Column("suggestion_count", sa.Integer(), nullable=False),
        sa.Column("latency_ms", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_usage_events_subject", "usage_events", ["subject"], unique=False)
    op.create_index(
        "ix_usage_events_request_id",
        "usage_events",
        ["request_id"],
        unique=True,
    )
    op.create_index(
        "ix_usage_events_created_at",
        "usage_events",
        ["created_at"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index("ix_usage_events_created_at", table_name="usage_events")
    op.drop_index("ix_usage_events_request_id", table_name="usage_events")
    op.drop_index("ix_usage_events_subject", table_name="usage_events")
    op.drop_table("usage_events")
    op.drop_table("sms_challenges")
    op.drop_index("ix_refresh_sessions_expires_at", table_name="refresh_sessions")
    op.drop_index("ix_refresh_sessions_subject", table_name="refresh_sessions")
    op.drop_table("refresh_sessions")
    op.drop_table("client_settings")
    op.drop_index("ix_history_records_expires_at", table_name="history_records")
    op.drop_index("ix_history_records_created_at", table_name="history_records")
    op.drop_index("ix_history_records_subject", table_name="history_records")
    op.drop_table("history_records")
    op.drop_index("ix_devices_user_id", table_name="devices")
    op.drop_table("devices")
    op.drop_index("ix_users_phone_hash", table_name="users")
    op.drop_table("users")
