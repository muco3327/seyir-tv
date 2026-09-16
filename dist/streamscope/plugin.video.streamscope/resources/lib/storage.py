# -*- coding: utf-8 -*-
import os
import sqlite3
import xbmc
import xbmcvfs
import xbmcaddon

ADDON = xbmcaddon.Addon("plugin.video.streamscope")

def get_db_path():
    profile_dir = xbmcvfs.translatePath(ADDON.getAddonInfo('profile'))
    if not os.path.exists(profile_dir):
        os.makedirs(profile_dir)
    return os.path.join(profile_dir, "streamscope.db")

def get_connection():
    db_path = get_db_path()
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS channels (
                id TEXT PRIMARY KEY,
                clean_name TEXT,
                display_name TEXT,
                group_title TEXT,
                logo_url TEXT,
                tvg_id TEXT,
                source_count INTEGER DEFAULT 1,
                last_watched TIMESTAMP
            )
        """)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS sources (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                channel_id TEXT,
                source_name TEXT,
                url TEXT,
                user_agent TEXT,
                referer TEXT,
                quality TEXT,
                status TEXT DEFAULT 'online',
                response_time_ms INTEGER DEFAULT 0,
                order_index INTEGER,
                FOREIGN KEY (channel_id) REFERENCES channels (id) ON DELETE CASCADE
            )
        """)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS search_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                query TEXT UNIQUE,
                searched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_group ON channels(group_title)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_name ON channels(clean_name)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_ch_src ON sources(channel_id)")
        conn.commit()

def save_aggregated_channels(aggregated_dict):
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("DELETE FROM sources")
        cursor.execute("DELETE FROM channels")

        for ch_id, ch in aggregated_dict.items():
            sources = ch.get("sources", [])
            cursor.execute("""
                INSERT INTO channels (id, clean_name, display_name, group_title, logo_url, tvg_id, source_count)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """, (
                ch_id,
                ch.get("clean_name", ""),
                ch.get("display_name", ""),
                ch.get("group_title", "Genel"),
                ch.get("logo_url", ""),
                ch.get("tvg_id", ""),
                len(sources)
            ))

            for idx, s in enumerate(sources):
                cursor.execute("""
                    INSERT INTO sources (channel_id, source_name, url, user_agent, referer, quality, order_index)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """, (
                    ch_id,
                    s.get("source_name", f"Kaynak {idx + 1}"),
                    s.get("url", ""),
                    s.get("user_agent", ""),
                    s.get("referer", ""),
                    s.get("quality", "HD"),
                    idx
                ))
        conn.commit()

def get_categories():
    init_db()
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("""
            SELECT group_title, COUNT(*) as count 
            FROM channels 
            GROUP BY group_title 
            ORDER BY group_title ASC
        """)
        return [{"group": row["group_title"], "count": row["count"]} for row in cursor.fetchall()]

def get_channels(group=None, search_query=None):
    init_db()
    with get_connection() as conn:
        cursor = conn.cursor()
        if search_query:
            query = f"%{search_query.strip()}%"
            cursor.execute("""
                SELECT * FROM channels 
                WHERE display_name LIKE ? OR clean_name LIKE ? 
                ORDER BY display_name ASC
            """, (query, query))
        elif group:
            cursor.execute("""
                SELECT * FROM channels 
                WHERE group_title = ? 
                ORDER BY display_name ASC
            """, (group,))
        else:
            cursor.execute("SELECT * FROM channels ORDER BY display_name ASC")

        return [dict(row) for row in cursor.fetchall()]

def get_channel_sources(channel_id):
    init_db()
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM sources WHERE channel_id = ? ORDER BY order_index ASC", (channel_id,))
        return [dict(row) for row in cursor.fetchall()]

def get_channel(channel_id):
    init_db()
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM channels WHERE id = ?", (channel_id,))
        row = cursor.fetchone()
        if row:
            ch = dict(row)
            ch["sources"] = get_channel_sources(channel_id)
            return ch
        return None

def update_source_status(source_id, status, response_time_ms):
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("UPDATE sources SET status = ?, response_time_ms = ? WHERE id = ?",
                       (status, response_time_ms, source_id))
        conn.commit()

def record_watch(channel_id):
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("UPDATE channels SET last_watched = CURRENT_TIMESTAMP WHERE id = ?", (channel_id,))
        conn.commit()

def get_recent_channels(limit=25):
    init_db()
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("""
            SELECT * FROM channels 
            WHERE last_watched IS NOT NULL 
            ORDER BY last_watched DESC 
            LIMIT ?
        """, (limit,))
        return [dict(row) for row in cursor.fetchall()]

def save_search_query(query):
    if not query or not query.strip():
        return
    q = query.strip()
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("""
            INSERT INTO search_history (query, searched_at) VALUES (?, CURRENT_TIMESTAMP)
            ON CONFLICT(query) DO UPDATE SET searched_at = CURRENT_TIMESTAMP
        """, (q,))
        conn.commit()

def get_search_history(limit=10):
    init_db()
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT query FROM search_history ORDER BY searched_at DESC LIMIT ?", (limit,))
        return [row["query"] for row in cursor.fetchall()]

def clear_search_history():
    init_db()
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("DELETE FROM search_history")
        conn.commit()

def get_channels_by_letter(letter):
    init_db()
    with get_connection() as conn:
        cursor = conn.cursor()
        query = f"{letter.upper()}%"
        cursor.execute("""
            SELECT * FROM channels 
            WHERE display_name LIKE ? OR clean_name LIKE ? 
            ORDER BY display_name ASC
        """, (query, query))
        return [dict(row) for row in cursor.fetchall()]
