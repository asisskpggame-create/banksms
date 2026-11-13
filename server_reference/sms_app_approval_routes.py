"""Server-side routes for `APP_SIDE_APPROVAL_SPEC.md`:
Add `GET /<PANEL_PATH>/sms-pending` and `POST /<PANEL_PATH>/sms-approve` to `app/web/sms_in.py`
with all 7 mandatory server safety locks from Section 5-1.
"""
import hashlib
import hmac
import logging
import time
from datetime import timedelta

from aiohttp import web
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from app.config import get_settings
from app.models import AuditEvent, Order, SmsDeposit, TopUp, utcnow
from app.services import sms_gateway, sms_parse, wallet
from app.services.commerce import review_payment
from app.web.security import token_ok
from app.web.server import DEPS, _body, client_ip, json_error

logger = logging.getLogger(__name__)
MIN_TOKEN = 20
MAX_TEXT = 4000
TS_TOLERANCE_MS = 5 * 60 * 1000       # ±5 minutes for HMAC timestamp
ORDER_TIME_TOLERANCE_S = 120          # 2 minutes tolerance for order vs SMS timestamp


def _verify_hmac(token: str, kind: str, public_id: str, fp: str, amount_minor: int, ts: int, sig: str) -> bool:
    msg = f"{kind}|{public_id}|{fp}|{amount_minor}|{ts}".encode("utf-8")
    expected_sig = hmac.new(token.encode("utf-8"), msg, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected_sig, (sig or "").strip().lower())


async def sms_pending(request: web.Request) -> web.Response:
    """GET /<PANEL_PATH>/sms-pending: returns waiting payments and server_now_ms."""
    deps = request.app[DEPS]
    settings = get_settings()
    expected = (settings.sms_gateway_token or "").strip()
    if len(expected) < MIN_TOKEN:
        return json_error(404, "درگاه پیامک خاموش است.")
    client = client_ip(request)
    if deps.limiter.blocked(client):
        return json_error(429, "تلاش‌های ناموفق زیاد بود؛ چند دقیقه بعد دوباره تلاش کنید.")

    token = request.headers.get("X-Token") or request.query.get("token") or ""
    if not isinstance(token, str) or not token_ok(token.strip(), expected):
        deps.limiter.record_failure(client)
        return json_error(401, "توکن نامعتبر است.")

    async with deps.session_factory() as session:
        items = await sms_gateway._pending_items(session, settings)

    server_now_ms = int(time.time() * 1000)
    return web.json_response({
        "ok": True,
        "server_now_ms": server_now_ms,
        "match_window_hours": max(1, getattr(settings, "sms_match_window_hours", 24)),
        "auto_max_toman": max(0, getattr(settings, "sms_auto_max_toman", 0)),
        "items": [
            {
                "kind": i.kind,
                "public_id": i.public_id,
                "due_minor": i.due_minor,
                "created_ms": int(i.at * 1000),
                "early": i.early,
            }
            for i in items
        ],
    })


async def sms_approve(request: web.Request) -> web.Response:
    """POST /<PANEL_PATH>/sms-approve: enforces all 7 mandatory safety locks before approving."""
    deps = request.app[DEPS]
    settings = get_settings()
    expected = (settings.sms_gateway_token or "").strip()
    if len(expected) < MIN_TOKEN:
        return json_error(404, "درگاه پیامک خاموش است.")
    client = client_ip(request)
    if deps.limiter.blocked(client):
        return json_error(429, "تلاش‌های ناموفق زیاد بود؛ چند دقیقه بعد دوباره تلاش کنید.")

    data = await _body(request)
    token = data.get("token") or request.headers.get("X-Token") or ""
    if not isinstance(token, str) or not token_ok(token.strip(), expected):
        deps.limiter.record_failure(client)
        return json_error(401, "توکن نامعتبر است.")

    kind = str(data.get("kind") or "")
    public_id = str(data.get("public_id") or "").strip()
    fp = str(data.get("fingerprint") or "").strip().lower()
    bank = str(data.get("bank") or "").strip()[:32]
    raw_sms = str(data.get("sms") or "")
    try:
        amount_minor = int(data.get("amount_minor") or 0)
        sms_received_ms = int(data.get("sms_received_ms") or 0)
        ts = int(data.get("ts") or 0)
    except (TypeError, ValueError):
        return json_error(400, "پارامترهای عددی نامعتبر هستند.")
    sig = str(data.get("sig") or "")

    now_ms = int(time.time() * 1000)
    if abs(now_ms - ts) > TS_TOLERANCE_MS:
        return json_error(401, "زمان امضای درخواست (ts) منقضی شده است.")
    if not _verify_hmac(expected, kind, public_id, fp, amount_minor, ts, sig):
        deps.limiter.record_failure(client)
        return json_error(401, "امضای HMAC درخواست نامعتبر است.")

    # Lock #2: Re-parse raw SMS on server and verify fingerprint & amount_minor match claim
    expected_fp = sms_parse.fingerprint(bank, raw_sms)
    parsed = sms_parse.parse(raw_sms)
    if fp != expected_fp or parsed.amount_minor is None or parsed.amount_minor != amount_minor:
        return web.json_response(
            {"ok": False, "status": "conflict", "reason": "عدم تطابق مبلغ یا اثرانگشت در پارس مجدد سرور"},
            status=409,
        )

    # Lock #5: Check server auto-approve & ceiling
    limit_minor = max(getattr(settings, "sms_auto_max_toman", 0), 0) * 10
    if not getattr(settings, "sms_auto_approve", True) or (limit_minor > 0 and amount_minor > limit_minor):
        return web.json_response(
            {"ok": False, "status": "conflict", "reason": "خارج از سقف مجاز یا غیرفعال بودن تأیید خودکار سرور"},
            status=409,
        )

    now = utcnow()
    async with deps.session_factory() as session:
        # Lock #1: Idempotency & claim-before-approve on SmsDeposit(fingerprint)
        existing = await session.scalar(select(SmsDeposit).where(SmsDeposit.fingerprint == fp))
        if existing is not None:
            if existing.status == "matched" and existing.matched_id == public_id:
                return web.json_response({"ok": True, "status": "duplicate"})
            return web.json_response(
                {"ok": False, "status": "conflict", "reason": "این پیامک قبلاً پردازش شده است"},
                status=409,
            )

        # Lock #3 & #4: Verify target payment is still waiting, due_minor == amount_minor, and created <= sms time
        pending_items = await sms_gateway._pending_items(session, settings)
        target = next((i for i in pending_items if i.kind == kind and i.public_id == public_id), None)
        if target is None or target.due_minor != amount_minor:
            return web.json_response(
                {"ok": False, "status": "conflict", "reason": "سفارش دیگر در وضعیت انتظار نیست یا مبلغ مطابقت ندارد"},
                status=409,
            )
        if (sms_received_ms / 1000.0) + ORDER_TIME_TOLERANCE_S < target.at:
            return web.json_response(
                {"ok": False, "status": "conflict", "reason": "زمان پیامک قدیمی‌تر از زمان ثبت سفارش است"},
                status=409,
            )

        # Claim deposit BEFORE executing approval
        dep_row = SmsDeposit(
            fingerprint=fp,
            bank=bank,
            amount_minor=amount_minor,
            status="matched",
            reason="",
            body=sms_parse.normalize(raw_sms)[:1000],
            matched_kind=kind,
            matched_id=public_id,
            decided_at=now,
        )
        session.add(dep_row)
        try:
            await session.commit()
        except IntegrityError:
            await session.rollback()
            return web.json_response({"ok": True, "status": "duplicate"})

        try:
            if target.early and not await sms_gateway._promote(session, target):
                raise ValueError("payment is no longer waiting")
            if kind == "order":
                await review_payment(session, public_id, 0, True, note="sms-app-auto")
            else:
                await wallet.review_topup(session, public_id, 0, True, note="sms-app-auto")
            # Lock #6: Audit log
            session.add(AuditEvent(
                actor_telegram_id=0,
                action="payment.sms_app_approve",
                object_type=kind,
                object_id=public_id,
            ))
            await session.commit()
        except Exception:
            await session.rollback()
            return web.json_response(
                {"ok": False, "status": "conflict", "reason": "امکان نهایی‌سازی تأیید سفارش وجود نداشت"},
                status=409,
            )

        await sms_gateway._tell_customer(session, deps.bot, target)
        await sms_gateway._tell_admins(
            session,
            deps.bot,
            settings.admin_ids,
            f"✅ {sms_gateway._label(target)} توسط اپ اندروید ({sms_gateway._toman(amount_minor)} تومان) تأیید شد.",
        )

    return web.json_response({"ok": True, "status": "approved"})

# NOTE: this reference is now merged (with hardening) into the bot's `app/web/sms_in.py` (v0.20.161) — use that file.

