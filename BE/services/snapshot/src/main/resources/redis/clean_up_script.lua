local opIdsKey = KEYS[1]
local deltasKey = KEYS[2]
local tombstonesKey = KEYS[3]
local maxScore = tonumber(ARGV[1])

local opIds = redis.call('ZRANGEBYSCORE', opIdsKey, '-inf', maxScore)
local opIdsRemoved = redis.call('ZREMRANGEBYSCORE', opIdsKey, '-inf', maxScore)

local deltasRemoved = 0
if #opIds > 0 then
    deltasRemoved = redis.call('HDEL', deltasKey, unpack(opIds))
end

local tombstonesRemoved = redis.call('ZREMRANGEBYSCORE', tombstonesKey, '-inf', maxScore)

return {opIdsRemoved, deltasRemoved, tombstonesRemoved}