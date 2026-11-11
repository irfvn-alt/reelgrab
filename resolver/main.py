
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from urllib.parse import urlparse
import yt_dlp

app = FastAPI(title="ReelGrab Resolver")


class LinkRequest(BaseModel):
    url: str


def missing_message(url):
    path = urlparse(url).path.lower()

    if "/stories/" in path:
        return "Story not found"
    if "/reel/" in path:
        return "Reel not found"
    if "/tv/" in path:
        return "Video not found"

    return "Post not found"


@app.get("/")
def home():
    return {
        "service": "ReelGrab Resolver",
        "status": "online"
    }


@app.post("/resolve")
def resolve_media(data: LinkRequest):
    url = data.url.strip()
    parsed = urlparse(url)

    allowed_hosts = {
        "instagram.com",
        "www.instagram.com",
        "instagr.am",
        "www.instagr.am"
    }

    if parsed.scheme != "https" or parsed.hostname not in allowed_hosts:
        raise HTTPException(
            status_code=400,
            detail="Invalid Instagram link"
        )

    options = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
        "format": "best[ext=mp4]/best",
        "socket_timeout": 20
    }

    try:
        with yt_dlp.YoutubeDL(options) as ydl:
            info = ydl.extract_info(url, download=False)

        if not info:
            raise Exception("No media found")

        if "entries" in info:
            entries = [item for item in info["entries"] if item]
            if not entries:
                raise Exception("No media found")
            info = entries[0]

        media_url = info.get("url")

        if not media_url:
            formats = info.get("formats") or []
            formats = [
                item for item in formats
                if item.get("url")
                and item.get("vcodec") != "none"
            ]

            if formats:
                media_url = formats[-1]["url"]

        if not media_url:
            raise Exception("No downloadable media")

        return {
            "title": info.get("title") or "instagram_media",
            "media_url": media_url,
            "headers": info.get("http_headers") or {}
        }

    except Exception:
        raise HTTPException(
            status_code=404,
            detail=missing_message(url)
  )
          
