-- Token bucket rate limiter, atomic check-refill-consume.
--
-- KEYS[1] = bucket key, e.g. "rl:user:123"
-- ARGV[1] = capacity         (max tokens, integer)
-- ARGV[2] = refill_rate      (tokens per second, float)
-- ARGV[3] = now_millis       (caller wall clock, integer)
-- ARGV[4] = requested        (tokens to consume, integer, usually 1)
--
-- Returns: { allowed (1|0), tokens_remaining (int floor), retry_after_millis (int) }

local key       = KEYS[1]
local capacity  = tonumber(ARGV[1])
local rate      = tonumber(ARGV[2])
local now       = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens      = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

if tokens == nil then
    -- New bucket: start full. Common policy; alternative is start empty.
    tokens = capacity
    last_refill = now
end

-- Refill based on elapsed time
local elapsed_seconds = math.max(0, now - last_refill) / 1000.0
tokens = math.min(capacity, tokens + (elapsed_seconds * rate))

local allowed = 0
local retry_after_ms = 0

if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
else
    -- How long until we have enough? (requested - tokens) / rate, in ms
    retry_after_ms = math.ceil(((requested - tokens) / rate) * 1000)
end

redis.call('HSET', key, 'tokens', tokens, 'last_refill', now)

-- TTL: time to fully refill an empty bucket + a buffer.
-- Keeps inactive buckets out of memory; LRU eviction is the backstop.
local ttl_ms = math.ceil((capacity / rate) * 1000) + 60000
redis.call('PEXPIRE', key, ttl_ms)

-- math.floor on the remaining tokens for a clean integer in headers
return { allowed, math.floor(tokens), retry_after_ms }