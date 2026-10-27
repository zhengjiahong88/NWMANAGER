# NWMANAGER：雙人協同刺刀 Groupfight

NWMANAGER 是 Java Swing 製作的可觀看刺刀戰鬥原型。玩家可建立四名 2v2 選手，或在第一階段
實驗 3v3 中建立六名選手，再觀看 Agent
依照位置、角度、速度、刺刀尖端、格擋方向與實際碰撞自行戰鬥。

[Version française](README.fr.md)

這份中文版著重說明「程式碼怎麼運作」，方便繼續修改戰鬥引擎。

## 1. 執行方式與介面流程

Windows 可以直接雙擊 `run.bat`。也可以在專案根目錄執行：

```powershell
javac -encoding UTF-8 -d .\out .\src\*.java
java -cp .\out Main
```

介面流程：

```text
主選單 → 遊玩 → 友誼賽
→ 編輯四名選手的姓名、戰術與能力
→ 開始 → MODERN_2V2 戰鬥畫面

主選單 → 遊玩 → 實驗 3v3 比賽
→ 編輯六名選手
→ 開始 → MODERN_3V3 Groupfight（包含 3v2、3v1、2v2、2v1、1v1 轉換）
```

介面可切換繁體中文與法文，模擬速度有 `0.25×`、`0.5×`、`1×`、`2×`、`4×`。

## 2. 現代 2v2 的核心狀態

四人都存活時，只建立一個包含四人的 `EngagementType.GROUP_2V2`，不是兩場互不相干的
1v1，也不是四人共用同一條圓形軌道。藍隊與紅隊各自保存一個 `TeamManeuver`，所以可以出現：

```text
藍隊：COMPRESS
紅隊：ROTATE_RIGHT
```

第一人死亡後，`EngagementManager` 會中止 `GROUP_2V2` 並建立真正的 `TWO_VS_ONE`。
原本已驗證的 `FIX/FLANK/SURVIVE`、上下雙重威脅、Double Block、背刺及防守者脫離邏輯
沒有因這次重構而改動。

## 3. 主要類別與責任

### `DuelSimulation`

戰鬥引擎本體，負責：

- 每個 tick 更新時間、感知與狀態；
- 選目標與做決策；
- 計算團隊及個人移動向量；
- 套用加速度、慣性、後退減速、碰撞與邊界；
- 推進攻擊、格擋、Chamber、恢復；
- 執行刺刀尖端與身體部位的碰撞判定；
- 產生決策、命中與死亡的證據。

### `GroupEngagement`

保存四名 Agent、藍紅隊中心、全體中心、配對、報復與反掩護資料，以及：

```java
TeamManeuver blueManeuver;
TeamManeuver redManeuver;
```

`team(Team)`、`opponents(Team)`、`teamCenter(Team)`、`enemyCenter(Team)` 將隊伍判斷集中在
實體方法內，避免各處重複寫藍紅分支。

### `TeamManeuverType`

```text
HOLD_LINE
ROTATE_LEFT / ROTATE_RIGHT
PRESS_FORWARD / GIVE_GROUND
SHIFT_LEFT / SHIFT_RIGHT
COMPRESS / EXPAND
CROSS_COVER
RECOVER_FORMATION
```

### `TeamManeuver`

這是持續存在的團隊意圖，不是單一 tick 的指令。它保存：隊伍、種類、開始時間、最短／偏好／
最長持續時間、開始時雙方中心、方向、內外側選手、信心與主要理由。

### `TeamManeuverAssessment`

保存某個候選動作的幾何評估：互相掩護、側向空間、前方壓力、敵方威脅、邊界安全、隊形品質、
攻擊線潛力、總分與拒絕理由。它只評估，不直接改變物理狀態。

### `LocalManeuver`

保存個人的短期修正，例如停住、左右移、斜進、斜退、重新靠近隊友、避開主要或次要刺刀線、
建立攻擊線。它現在是 `TeamManeuver` 上的局部修正，不再是四人移動的主引擎。

### `Pos`

位置和向量統一由 `Pos(x, y)` 表示。加減、縮放、除法、內積、長度和角度都是 `Pos` 的實體方法，
因此戰術程式不需要一直傳遞鬆散的 `x`、`y`。

## 4. 團隊動作如何被選出

`updateTeamManeuvers()` 每次先判斷現有動作是否仍需持續。每隊獨立處理，紅隊不會複製藍隊選擇。

用兩名隊員的決策及團隊能力得到 `0～1` 的品質 `q`：

```text
最短時間 = 0.70 + 0.40q 秒
偏好時間 = 1.40 + 1.10q 秒
最長時間 = 3.00 + 1.00q 秒
```

最短時間以前，除非發生真正危險，否則不換動作。超過偏好時間才重新評估；超過最長時間後，
重複同一動作會扣分，避免永遠卡在相同行為。

`assessTeamManeuver()` 的共同分數為：

```text
共同分數 = 0.20 × 掩護保持
         + 0.18 × 側向空間
         + 0.14 × 邊界安全
         + 0.16 × 隊形品質
         + 0.14 × 攻擊線潛力
```

再加上各種類的條件：遠距離偏向 `PRESS_FORWARD`、近距離且受威脅可 `GIVE_GROUND`、隊友過遠
偏向 `COMPRESS/RECOVER_FORMATION`、間距太小而有兩條攻擊線時偏向 `EXPAND`。

程式會加入受決策能力限制的微小誤差。最後只在接近最高分的選項內抽選：

```text
候選窗 = 0.10 + 0.18 × (1 - q)
權重   = exp((候選分數 - 最高分) / 0.10)
```

因此高能力 Agent 較一致，低能力 Agent 較可能選擇「仍可行但不是最好」的方案。亂數由 seed 控制，
同 seed 的每次重播仍完全一致。

## 5. 中斷與防止 Thrashing

下列狀況可立刻中斷團隊動作：攻擊交換、Revenge、Counter-cover、Block stun、即將撞牆、隊友嚴重
分離、敵人穿進兩名隊友之間、死亡、進入 `TWO_VS_ONE`。

如果只是隊形危機，程式不會每一 frame 重新建立同一個動作：

```text
隊友分離 → RECOVER_FORMATION
敵人穿線 → COMPRESS
```

修正後的 500 seeds 中，engagement、target、intent 與 primary mark thrashing 全部都是 `0`。

## 6. 雙人旋轉的幾何

`teamManeuverVelocity()` 先算：

```text
teamCenter  = 兩名隊友位置平均
enemyCenter = 兩名敵人位置平均
forward     = normalize(enemyCenter - teamCenter)
lateral     = perpendicular(forward) × 動作方向
```

這裡沒有用四人共同中心產生推進力，所以不是舊式全體公轉。

旋轉開始時會決定內外側：較靠近 groupfight 中心的是內側；距離相同時，依轉向上的位置判定。
角色在同一個 `TeamManeuver` 期間保持不變，避免每 tick 互換。

基準速度：

```text
外側：側向 0.76 m/s，向前 0.12 m/s
內側：側向 0.37 m/s，向前 0.15 m/s
步法倍率 = 0.82 + 0.002 × footwork
```

在沒有刺刀、碰撞或邊界危險時，要求的側向投影約為外側 `0.62 m/s`、內側 `0.42 m/s`。
這只是期望速度，之後仍會經過加速度、慣性、後退 `0.62` 倍減速、速度上限及碰撞處理。
500 seeds 的非緊急實際平均為外側比內側快 `0.135 m/s`。

## 7. 速度如何合成

`modernGroupVelocity()` 明確建立六個部分：

```text
teamManeuverVelocity
localManeuverVelocity
formationVelocity + partnerVelocity
weaponAvoidanceVelocity
collisionVelocity
boundaryVelocity
```

一般 `LocalManeuver` 乘上 `0.58`，避免重新壓過團隊方向；若是可信的刺刀線逃離則保留完整強度。

優先順序：

```text
即將撞牆或碰撞
→ 即將命中的攻擊
→ 格擋、Revenge、Counter-cover
→ 刺刀線閃避
→ 個人修正
→ 團隊動作
→ 隊形修正
```

所以 `ROTATE_RIGHT` 不會阻止格擋，也不會阻止第四名玩家的威脅反應。

## 8. 真正的停頓

`LocalManeuverType.HOLD_POSITION` 的持續範圍是 `0.18～0.42 秒`。當 Agent 主動 Hold 時，
自己的團隊推進和普通隊形修正會暫停，但刺刀、碰撞或邊界危險仍可強制移動。

統計分開記錄：

```text
intentionalHoldDuration      主動選擇停住
incidentalLowSpeedDuration   慣性或幾何造成的低速
```

最終 500 seeds 分別是 `13.233 秒` 與 `14.783 秒`，不再把只有一兩個 frame 的低速誤認成可見停頓。

## 9. 攻擊與身體碰撞

攻擊仍經過：

```text
ATTACK_PREPARE → ATTACK_HOLD（可省略或持續）
→ ATTACK_ACTIVE → RECOVERY
```

上刺範圍較大，下刺較快；身體垂直角度會改變準備、有效與恢復時間，左右 yaw 會改變刺刀線。
後退比前進慢。Agent 不會永遠正面相對，可以側移、繞行、Hold 中移動或脫離區域。

命中必須是刺刀尖端掃過實際身體區域：頭部小、軀幹大、腿部常代表刺得過長或瞄準誤差。
格擋必須方向、時間和幾何都正確。背後攻擊不能向後格擋，只能靠感知、轉身、步法與隊友保護。

這次重構沒有增加攻擊加成，也沒有縮短準備時間或放寬距離。所有普通刺擊仍須通過
`assessAttackOpportunity()` 與 `TeamAttackArbitration`。

## 10. 能力值如何影響程式

技術能力：

- `footwork`：速度控制、旋轉品質、後方保護與團隊動作倍率；
- `distanceControl`：感知距離誤差與出手距離判斷；
- `attackTiming`：出手窗口、Chamber/操控的執行品質；
- `defenseTiming`：格擋啟動與方向切換；
- `chamber`、`doubleBlock`：特殊防守的成功窗口。

心理與認知能力：

- `aim`：刺刀方向和身體部位誤差；
- `reaction`：發現威脅到開始反應的延遲；
- `decisionMaking`：候選評分誤差、團隊動作穩定度與目標選擇；
- `teamwork`：掩護、隊友距離與團隊選擇品質。

性格能力不直接代表強弱，只改變偏好：

- `aggression`：主動施壓和攻擊傾向；
- `zoneExit`：脫離目前區域或換目標的傾向；
- `fiftyFifty`：願不願意接受同時出手；
- `pressure`：用 Hold、角度與步法逼迫對手格擋。

能力值不會直接寫成命中百分比。它們改變「看到什麼、多久反應、估計多準、選哪個可行方案」，
最後結果仍由實際位置與時間決定。

## 11. 證據、Trace 與死亡原因

`AttackOpportunityEvidence` 在 commit 時凍結觀察資料：身體／武器角度、狀態、剩餘時間、刺刀線、
進線速度、ETA、掩護與信心。`TARGET_MOVING_INTO_LINE` 必須真的預測到目標穿越刺刀走廊；沒有證據
就不會用這個理由攻擊。

`DecisionTrace` 現在分開記錄：

```text
TEAM_MANEUVER
LOCAL_MOVEMENT
ATTACK / ATTACK_REJECTION
BLOCK
REVENGE / COUNTER_COVER
TARGET_ATTENTION
TWO_VS_ONE_COORDINATION
```

`KillCausalChain` 也分開保存團隊動作與個人動作：

```text
TeamManeuver
→ LocalManeuver
→ 攻擊決策
→ 被攻擊者反應
→ 隊友掩護決策
→ 實際命中／格擋／死亡
```

找不到的事件會明確寫成 `NONE_OBSERVED`，不會在死亡後反推一個不存在的原因。

## 12. 畫面與視覺驗證

戰術 debug 會顯示藍紅隊目前 `TeamManeuver`、內外側選手、持續時間與速度貢獻。產生標準驗證圖：

```powershell
java -cp .\out VisualValidationCapture 41 73 119
java -cp .\out VisualValidationCapture --representative
```

每個 `out/visual-validation/seed-N/` 包含：

- `contact-sheet.png`：1× 速度下的 16 個畫面；
- `trajectories.png`：第一人死亡以前的四條軌跡；
- `timeline.txt`：團隊／個人動作、角色、持續時間、原因；
- `decisions.txt`：攻擊機會與凍結證據；
- `first-kill.txt`：死亡解釋與因果鏈；
- `motion.txt`、`suspicions.txt`：移動稽核。

舊的平淡版本約為團隊偏向 `8.2%`、個人微動 `61.4%`。目前混合版本為：

```text
團隊動作 39.8%
個人修正 27.9%
隊形修正 19.9%
碰撞與邊界 12.2%
```

## 13. 最終 500 seeds 結果

```powershell
java -cp .\out FinalAcceptanceTest 500
```

```text
500/500 完成
timeout / stalemate / thrashing = 0
無效位置 / 離開競技場 = 0
舊系統啟動 = 0
決定性重播失敗 = 0
500/500 第一個死亡都有 KillCausalChain

TeamManeuver 開始次數：4052
ROTATE_LEFT / RIGHT：1117 / 1162
PRESS_FORWARD / GIVE_GROUND：519 / 23
COMPRESS / EXPAND：345 / 90
CROSS_COVER / RECOVER_FORMATION：14 / 768
累計側向距離：3556.485 m
隊形面積變化：5194.712

第一死因：普通攻擊 415、Revenge 84、Chamber 1
Revenge：1005 次機會、104 次 commit、84 次擊殺
死亡後 TWO_VS_ONE：500/500
```

## 14. 測試指令

```powershell
java -cp .\out TeamManeuverTest
java -cp .\out ModernGeometryTest
java -cp .\out ModernGroup2v2Test
java -cp .\out TwoVsOneCoordinationTest
java -cp .\out AttackOpportunityTest
java -cp .\out DecisionExplanationTest
java -cp .\out AttributeBehaviorAudit
java -cp .\out FinalAcceptanceTest 500
```

`TeamManeuverTest` 包含：單隊旋轉、雙隊不同旋轉、旋轉對壓迫、個人 Hold、刺刀線優先、Shift、
Compress、Expand、五秒動態場景與同 seed 決定性。

## 15. 舊欄位說明

`LEGACY_2V2` 仍可編譯，部分相容診斷也還保留 `GroupPhase.ROTATE`、舊方向與角速度欄位。
它們現在只服務舊報表與相容測試，不再替 `modernGroupVelocity()` 產生四人共用切線，請勿把它們
重新接回現代友誼賽。

## 16. 實驗 3v3 里程碑 1：團隊移動核心

3v3 使用獨立入口，不會改變既有 `MODERN_2V2`：

```powershell
java -cp .\out Main modern-3v3 41
java -cp .\out Main controlled-3v3 41
```

### 建立六名玩家的程式流程

`TeamConfiguration3` 將隊名與三份 `AgentConfiguration` 包在同一個物件中。主選單的
「實驗 3v3 比賽」會開啟六份 `PlayerEditorPanel`，因此姓名、戰術和全部能力都能在開戰前
修改。按下開始後，`Main.showCombat3()` 將兩個 `TeamConfiguration3` 交給 `MapPanel`；
`MapPanel` 再建立 3v3 專用的 `DuelSimulation`。

`resetExperimental3v3()` 生成六個 `Musketeer`，但只向 `EngagementManager` 登記一個
`EngagementType.GROUP_3V3`。這點很重要：目前的 3v3 不是把 A1/B1、A2/B2、A3/B3 拆成三場
互不相干的 1v1。一般畫面也不畫三條固定 target 線；只有開啟 debug 才顯示觀察資訊。

### `TeamFormation3` 如何由位置求角色

每隊各有一個 `TeamFormation3`。它先用三個 `Pos` 求平均中心，再把「我方中心指向敵方中心」
正規化成 `forward`，並以垂直向量建立 `lateral`。每名玩家的位置減掉隊伍中心，再和
`lateral` 做 dot product：投影最小者是 `LEFT`、中間者是 `CENTER`、最大者是 `RIGHT`。
所以角色會隨真實站位交換，不是出生時寫死的編號。

程式還加入兩層防抖：角色至少維持 `0.55–0.90 秒`，而候選者必須跨過約 `0.30 公尺` 的
空間差才交換。維持時間受決策與團隊能力調整。這能保留真正的交叉換位，同時避免兩人投影
非常接近時，每個 tick 不斷互換左右角色。

隊形同時量測：

- `width`：三人在側向軸上的最大投影差；
- `depth`：三人在前向軸上的最大投影差；
- `cohesion`：左右翼和中間選手是否接近約 `1.35 m` 的理想間隔；
- `center`、`forward`、`lateral`：之後所有團隊移動共用的 `Pos` 幾何資料。

### 三人如何執行同一個旋轉

藍紅兩隊分別保存自己的 `TeamManeuver`，因此一隊可以 `ROTATE_RIGHT`，另一隊同時
`PRESS_FORWARD`。旋轉時不會把三人設成相同速度：外側、中央、內側的切向基礎速度分別是
`0.84 / 0.56 / 0.28 m/s`。之後才疊加步法係數、隊形位置修正、加速度與慣性、後退減速、
碰撞和邊界 steering。視覺結果是一條會彎曲與重整的三人線，而不是三個角色被鎖在圓形軌道。

`Group3v3Metrics` 記錄隊形寬度、深度、凝聚度、角色交換、防抖失敗、團隊動作次數，以及
旋轉時外側和內側的實際切向速度差。這些統計只觀察結果，不反過來控制 AI。

### 為何目前不能攻擊

里程碑 1 的目的只驗證六人幾何與連續移動。`DuelSimulation.update()` 遇到 3v3 scenario 時會
直接走 `updateExperimental3v3()`；此路徑不呼叫攻擊選擇、格擋、刺刀碰撞或傷害解析。六人
固定維持 `NEUTRAL / WAIT` 和 100 生命。攻擊機會、死亡，以及
3v3 陣亡後轉為 2v2/2v1，會在後續里程碑逐步加入；本版沒有假裝這些功能已完成。

### 自動測試與視覺產物

```powershell
java -cp .\out Experimental3v3Test
java -cp .\out VisualValidation3v3Capture
```

`Experimental3v3Test` 驗證：場上只有一個 `GROUP_3V3`、完全沒有攻擊、初始幾何角色正確、
小幅交叉不會洗牌、持續且明顯的交叉會換位、兩隊動作互相獨立、旋轉實際速度符合
外側 > 中央 > 內側，以及相同 seed 完全可重播。另以 `Modern2v2Test`、`TeamManeuverTest`、
`ModernGroup2v2Test` 保護既有 2v2。

`VisualValidation3v3Capture` 預設輸出 seeds 41、73、119。每個
`out/visual-validation-3v3/seed-N/` 目錄包含：

- `contact-sheet.png`：前十五格每隔 1 秒取樣，最後一格顯示第一人陣亡；
- `trajectories.png`：六名玩家的彩色移動軌跡；
- `formations.txt`：每隊當下的左／中／右角色、寬度、深度、凝聚度與 `TeamManeuver`。

## 17. 實驗 3v3 里程碑 2：有限注意力

每名 Agent 現在各有一個 `AttentionAllocation`。既有 2v1 使用的 `MultiThreatAttention` 沒有被
修改，因此 3v3 的三層注意力不會暗中改變 `MODERN_2V2` 或 `TWO_VS_ONE`。

### 三層注意力資料

```java
Musketeer primaryThreat;
Musketeer secondaryThreat;
Musketeer peripheralThreat;

double primaryWeight;      // 約 0.50–0.66
double secondaryWeight;    // 剩餘的主要份額
double peripheralWeight;   // 約 0.10–0.17
```

三個敵人必須互不相同，權重每次都正規化為 `1`。權重不是命中機率，而是分配觀察更新的頻率
和資料精度。`group3ThreatSalience()` 依距離、視野角度、接近速度、明顯攻擊以及近距離背後威脅
計算顯著程度。

### 為何看見 B2 不會立刻改成 B2

排名最高的敵人不會直接寫進 `primaryThreat` 或 `target`。普通挑戰者必須比目前威脅高至少
`0.16`，而且目前注意力已維持 `0.55–0.90 秒`，才會先進入 `pendingPrimary`。接下來還要等待
約 `0.14–0.38 秒` 的轉向成本；實際長度由反應與決策能力決定。短距離即將命中的攻擊可使用
`0.08 秒` 緊急延遲，但依然不是瞬間切換。

切換完成時才會更新 `primaryThreat`，並在 `precisionPenaltyUntil` 以前保留短暫不確定性。
次要威脅也有 `0.42 秒` 的防抖和 `0.14` 分數差，避免 Secondary／Peripheral 每個 tick 互換。
移動與身體朝向只追隨已完成切換的主要威脅，不會追著 `pendingPrimary` 跑。

### `OpponentAwareness` 的資料品質

每個敵人的 awareness 現在記錄：

- `attentionLevel`；
- `observationCooldown` 與觀察次數；
- `detailConfidence`；
- `lastObservationTime`；
- `stateDetailFresh`，代表攻擊／格擋狀態是否真的被辨識。

三層的大致更新週期為：

```text
Primary     0.075–0.125 秒
Secondary   0.17–0.29 秒
Peripheral  0.42–0.72 秒
```

Primary 能較準確觀察位置、速度、身體角度、刺刀方向與戰鬥狀態。Secondary 更新較慢、誤差
較大，只在明顯動作或細節判讀成功時更新狀態。Peripheral 的位置誤差約為基礎值的 `4.60 倍`，
主要保留最後位置、粗略移動與危險警示；它永遠不直接複製精確的攻擊或格擋狀態，
`stateDetailFresh` 固定為 false。

### 介面、統計與驗證

按 `D` 後會顯示每名 Agent 的 `P/S/R`、三個權重、等待切換的敵人及剩餘時間。左右資訊卡也會
顯示三層敵人代號。`Group3v3Metrics` 統計 Primary 延遲及完成切換、Secondary 切換、Peripheral
偵測與平均權重。

```powershell
java -cp .\out Experimental3v3AttentionTest
java -cp .\out VisualValidation3v3Capture
```

注意力測試 A–E 驗證三層分配、權重正規化、觀察頻率遞減、信心遞減、非瞬間切換、Peripheral
不會讀取精確狀態，以及相同 seed 的決定性。視覺驗證目錄新增 `attention.txt`。此里程碑的
隔離測試當時尚未啟用攻擊。

## 18. 實驗 3v3 里程碑 3：物理掩護網路

藍紅兩隊現在各自擁有一個 `CoverNetwork`。它和既有 2v2 的 `CoverAssessment` 分開，沒有修改
原本 Revenge、Counter-cover 或 `TWO_VS_ONE` 的判定。

### 為何每隊有 18 個 `CoverLink`

每隊有三個可能的保護者。每位保護者可掩護另外兩位隊友，並分別針對三名敵人評估：

```text
3 名 protector × 2 名 protectedAlly × 3 名 threateningEnemy = 18
```

`CoverLink` 是不可變 record，保存：

```java
Musketeer protector;
Musketeer protectedAlly;
Musketeer threateningEnemy;
boolean viable;
double alignEta, prepareEta, contactEta, punishEta;
double lineSafety, perceptionConfidence, quality;
String rejectionReason;
```

`CoverNetwork.rebuild()` 將所有連結放入 `incomingLinks`（誰正在被掩護）和 `outgoingLinks`
（誰能提供掩護）。`viableRelations` 只保留本 tick 真正成立的關係，用來計算建立與斷裂。

### ETA 與幾何公式

連結只讀取保護者的 `OpponentAwareness.lastKnown`。敵人沒有被看見而且記憶已到期時，直接回傳
`enemy_untracked`，不會偷看真實座標。

```text
alignEta   = 刺刀方向誤差 / 預估轉身速度
prepareEta = 目前狀態仍需的準備時間
contactEta = max(0, 距離 - 刺刀距離 - 身體半徑) / 預估移動速度
punishEta  = alignEta + prepareEta + contactEta
```

敵人的 `threatEta` 也以最後已知位置、刺刀角度、距離與狀態計算。`lineSafety` 量測其他隊友到
「保護者 → 威脅敵人」線段的最小距離，避免所謂掩護其實會刺中或穿過隊友。

```text
quality = 0.38 × 時間優勢
        + 0.23 × 刺擊線安全
        + 0.20 × 感知信心
        + 0.07 × 與敵人的接近程度
        + 0.12 × 能否到達被保護隊友
        + 0.08 × 團隊合作與決策品質
```

下列情況會產生明確拒絕原因：

- 感知信心低於 `0.15`；
- `lineSafety < 0.12`；
- 保護者與隊友相距超過 `2.85 m`；
- `punishEta > threatEta + 0.42 s`；
- `quality < 0.34`。

### 網路統計如何計算

```text
connectivity = viableLinks / 18

某玩家的 coverage =
  對三名敵人各取一條品質最高的 incoming viable link，再求平均

weakestMemberCoverage = 三名隊友中最低的 coverage
```

因此右側翼離隊時，指向右側翼的連結會斷，但左側翼的關係可以繼續存在。中間玩家通常自然有
較多連結，是因為位置能接觸兩側，不是程式給 Center 隱藏加成。

### 對移動和團隊動作的影響

當下一次 `TeamManeuver` 需要重新選擇時：

- `weakestMemberCoverage < 0.10` 優先 `RECOVER_FORMATION`；
- `connectivity < 0.16` 優先 `COMPRESS`。

未被掩護的玩家會得到微弱的回到隊伍中心向量；Center 會略微靠向最弱的側翼。這些向量會和
原本的慣性、隊形、碰撞及邊界向量相加。旋轉期間只套用 `18%` 掩護修正，避免破壞里程碑 1
已驗證的「外側 > 中間 > 內側」速度差。

### Debug、測試與輸出

`D` 模式只畫每位被保護者最重要的有效連結，不把全部 36 條評估線塞滿畫面。文字會顯示有效
連結數、connectivity、最弱玩家與 coverage；左右玩家卡也顯示自己的 coverage。

```powershell
java -cp .\out Experimental3v3CoverNetworkTest
java -cp .\out VisualValidation3v3Capture
```

掩護測試 A–E 驗證 18 個候選關係、物理有效連結、單側翼離隊後只局部斷線、網路影響下一個
團隊動作、Primary/Peripheral 信心差異與決定性。每個視覺驗證目錄新增
`cover-network.txt`。這組隔離測試仍會關閉攻擊，避免碰撞干擾網路量測。

## 19. 實驗 3v3 里程碑 4：攻擊計畫與物理命中

正式的 `MODERN_3V3` 與 `CONTROLLED_3V3` 現在會攻擊。3v3 使用新的 `TeamAttackPlan`，不修改
2v2 的 `TeamAttackArbitration`。一份計畫保存零到兩名攻擊者、各自的 `AttackOpportunity`、
留下的掩護者、兩條線的安全值、恢復風險、理由與建立時間。

### `AttackOpportunity` 如何讀取三人戰場

Agent 只會評估目前的 Primary 與 Secondary。Peripheral 即使對應到真實 Java 物件，也會以
`LOW_CONFIDENCE_PERCEPTION` 拒絕，避免 AI 偷讀第三名敵人的精確狀態。

攻擊候選先使用 awareness 的最後位置計算：

```text
impactEta = 對準時間 + 準備時間 + 接觸時間 + 0.04
lineSafety = 兩名隊友到「攻擊者 → 目標」線段的最小安全程度
```

`OpposingCoverSummary` 再從目標隊伍的 `CoverNetwork` 找出「保護此目標、針對此攻擊者」的
有效連結。保護者還必須被攻擊者看見或記得，否則不會進入摘要。

```text
fastestCoverer
fastestPunishEta
secondPunishEta
credibleCoverers
combinedRisk
```

第一名保護者的 ETA 和攻擊者「命中 + 恢復」時間比較。第二名最多增加自己風險的 `35%`，不會
把所有敵人危險直接相加。攻擊者自己的 coverage 最多可降低 `42%` 最終 Revenge 風險。

拒絕條件包含：超出實際刺刀範圍、隊友擋線、信心低於 `0.32`、武器誤差超過 `48°`、敵方
掩護更快、攻擊窗口不足或決策分數太低。

### 為什麼最多只能兩人攻擊

計畫會排序每名 Agent 的最佳機會。第二名攻擊者只有在下列條件全部成立時加入：

- 兩人攻擊不同目標；
- 兩條刺擊線不危險交叉；
- 第三名隊友沒有被選為攻擊者；
- 第三名隊友仍有至少一條有效 `CoverLink`。

即使三人同時具有機會，第三名也不會加入。`tripleAttackerPlans` 必須保持 `0`，並由
`tripleAttackRejections` 記錄拒絕。這不是把三個角色同步按下攻擊鍵，而是保留一名真正的
掩護者。

### 攻擊、格擋與恢復的狀態推進

被選中的 Agent 實際經過：

```text
ATTACK_PREPARE → ATTACK_ACTIVE → RECOVERY
```

若目標在出手前離開範圍，既有 `ordinaryDirectTargetEscaped()` 仍可把直接攻擊轉成 Hold。
防守者則依自己的 awareness 排程反應：

```text
基礎延遲 = 0.36 - 0.0024 × reaction
Primary     +0.00 秒
Secondary   +0.09 秒
Peripheral  +0.22 秒
方向資料不新鮮 +0.10 秒
```

延遲結束後才進入 `BLOCK_PREPARE`，防守 timing 仍會影響準備時間與上下判斷。只有格擋方向
正確、角色真的面向攻擊線，而且刺刀尖端發生掃掠碰撞時才算 Block；成功後攻擊者進入
`BLOCK_STUN`。

沒有命中會進入 `RECOVERY`。恢復移動額外加入 `0.38 m/s` 的遠離目標向量，因此沒有取消原本
「攻擊恢復時拉開距離」的功能。命中仍完全交給 `resolveAttack()`、掃掠刺刀尖端、身體區域與
`checkHit()`；計畫分數本身不能直接造成傷害。

### 為何目前第一人死亡後停止

這是里程碑 4 當時的限制：為避免錯誤拆成三場決鬥，第一人死亡後先停止。里程碑 6 已正式
加入 `GROUP_3V2`、`GROUP_3V1`，並在適當人數把控制權交還 2v2、2v1、1v1 引擎。第一死仍會
保存 `KillExplanation` 與 `KillCausalChain`。

### 測試與驗證檔

```powershell
java -cp .\out Experimental3v3AttackPlanTest
java -cp .\out VisualValidation3v3Capture
```

攻擊測試 A–E 驗證多名掩護者摘要、安全雙攻擊、禁止第三名攻擊者、物理 Hit／Block／Kill、
恢復後退與決定性。Seeds 41、73、119 的視覺資料新增：

- `attacks.txt`：團隊攻擊計畫、格擋、命中與停止事件；
- `first-kill.txt`：第一死的原因和完整因果鏈。

前十五格 contact sheet 每隔 `1 秒` 取樣。如果仍未死亡，產生器會在背景繼續模擬到最多
30 秒；死亡後最後一格會改畫第一死的確切狀態，確保圖片與 `first-kill.txt` 都不是憑空推測。

## 20. 實驗 3v3 里程碑 5：Revenge 與反掩護

里程碑 5 把格擋或刺空之後的懲罰推廣到 3v3，但不共用 `MODERN_2V2` 的仲裁狀態。一個交換被
刻意限制為「原始攻擊 → 一名主要 Revenge 執行者 → 一名主要反掩護者」。Revenge 或反掩護
被格擋、刺空或結束後，交換立即關閉，不會再遞迴建立下一輪 Revenge。

### 如何選擇主要 Revenge 執行者

受威脅者的兩名隊友會各自建立 `TeamRevengeCandidate`。候選者不能偷讀真實敵人：原攻擊者
必須是其 Primary 或 Secondary，而且觀察信心至少為 `0.24`；Peripheral 的資料不足以發動
這種精確懲罰。

```text
impactEta = 感知到的對準時間
          + 剩餘準備時間
          + 進入刺擊距離的時間

vulnerability = 剩餘 BLOCK_STUN + 恢復
             或剩餘 RECOVERY
             或刺空後的正常恢復
```

刺擊線會同時檢查兩名隊友。系統也會計算「候選者離開掩護位置後，其他人仍保留多少條有效
CoverLink」，所以最近的人不一定最適合 Revenge。

```text
score = 0.36 × 時間優勢
      + 0.20 × 刺擊線安全
      + 0.17 × 技術與團隊能力
      + 0.12 × 保留下來的掩護
      + 0.15 × 面對反掩護的安全
```

候選者若正在攻擊、恢復、硬直、距離太遠、資訊不足、被隊友擋線、趕不上窗口或分數太低，
就會被拒絕。只有最高分的有效候選者進入 `COMMIT_REVENGE`；另一人繼續移動與維持掩護。

### 只允許一名主要反掩護者

Revenge 確定後，原攻擊者的兩名隊友才會接受評估。評估使用他們對 Revenge 執行者的感知、
對準／準備／接觸 ETA、刺擊線安全，以及決策和團隊能力。最高分的有效候選者進入
`COUNTER_COVER`，先以 Hold 建立武器威脅；只有 Revenge 已真正出手且進入物理刺擊線時才釋放。

反掩護不會直接用分數取消 Revenge。它的 ETA 只會影響候選選擇，之後仍必須真的轉向、進入
距離並通過刺刀碰撞。其他 Agent 不會同步攻擊，也不會產生無限反制鏈。

### 物理解算、Debug 與統計

Revenge 和反掩護沿用普通攻擊的狀態、上下格擋、身體區域與掃掠碰撞。統計分開記錄候選評估、
機會、出手、拒絕、取消、格擋、刺空、命中與擊殺。`preventedRevengeChains` 記錄被主動截斷的
後續 Revenge，證明交換不會遞迴。

按 `D` 可看到 Revenge 執行者、目標、保留連結數、反掩護風險與分數，以及反掩護者的 ETA。
`attacks.txt` 現在也包含 `GROUP3_REVENGE_*`、`GROUP3_COUNTER_COVER_*` 和
`GROUP3_EXCHANGE_FINISHED`。

```powershell
java -cp .\out Experimental3v3RevengeTest
java -cp .\out VisualValidation3v3Capture
```

測試 A–E 保證只有一名 Revenge 執行者、風險值有界、最多一名反掩護者、沒有無限反制鏈、
自然交換會物理解算且相同 seed 具有決定性。里程碑 6 現在會在第一死後繼續比賽。

## 21. 實驗 3v3 里程碑 6：局部 Cluster 與人數轉換

里程碑 6 明確分開兩件事：「局部幾何關係」只是一段描述；「人數真的改變」才會重建戰鬥
架構。比賽不再於第一人死亡時停止，而會持續到其中一隊全滅。

### `LocalEngagementCluster` 不會控制玩家

每 `0.15 秒`，系統會用存活敵人建立二分圖。兩名敵對 Agent 距離低於 `2.75 m`，或任一方向
的最佳命中 ETA 低於 `0.85 秒`，就建立一條互動邊。相連成分會形成
`LocalEngagementCluster`，保存雙方成員、中心、半徑、最低 ETA、孤立信心、是否仍受支援及原因。

Cluster 可描述中央混戰、暫時 2v1、受爭奪的側翼或真正孤立；但它不會建立 `DUEL`、不會建立
`TWO_VS_ONE`、不會鎖定目標，也不會命令整隊旋轉。

### 孤立必須同時考慮時間與掩護

若一名 Agent 在局部同時面對多名敵人：

```text
isolationConfidence = 0.34 × 局部人數劣勢
                    + 0.34 × CoverNetwork 損失
                    + 0.32 × 最快隊友支援延遲
```

信心必須達到 `0.66` 並持續約 `0.35 秒`，才算 durable isolation。只是在畫面上離隊不夠；
若圈外隊友仍能在攻擊前後及時介入，Cluster 會標記為 `contested`。

### 3v2 與 3v1 仍是集體戰鬥

每次死亡後都會清除死亡目標、觀察資料、攻擊計畫、掩護摘要和舊 Cluster，接著立刻重算隊形與
`CoverNetwork`。

```text
3 對 2 → GROUP_3V2
3 對 1 → GROUP_3V1
```

3v2 不會自動拆成 `2v1 + 1v1`。三人方保留三點隊形，兩人方使用調整過的左右位置。3v1 時，
兩名攻擊者可以從不同刺擊線評估同一目標，但仍禁止三人同時刺擊，而且必須保留一名掩護者。

### 回到已驗證的戰鬥引擎

只要雙方都不再有三人，3v3 集體引擎就會關閉並執行 handoff：

```text
2 對 2 → EngagementType.GROUP_2V2
2 對 1 → EngagementType.TWO_VS_ONE
1 對 1 → EngagementType.DUEL
```

Handoff 使用既有建構流程、`FIX/FLANK/SURVIVE`、隊伍動作、決策與物理碰撞；沒有重新校準
已驗證的 `TWO_VS_ONE`。後續死亡可繼續從 2v2 轉成 2v1，再轉成 1v1，最後進入 `COMPLETE`。

### Debug、統計與測試

按 `D` 會顯示目前階段，以及每個 Cluster 的成員、ETA、孤立信心和原因。統計會記錄 Cluster、
暫時 2v1、contested、durable isolation、3v2／3v1 建立、三種 handoff 與死亡參照清理。

```powershell
java -cp .\out Experimental3v3TransitionTest
java -cp .\out VisualValidation3v3Capture
```

測試 A–G 驗證受支援的暫時 2v1、持續孤立、3v2 不拆分、集體 3v1、三種既有引擎 handoff、
決定性，以及自然完整比賽不產生無效座標。`contact-sheet.png` 保留第一死畫面，
`transition-sheet.png` 顯示之後八個時間點；`transitions.txt` 記錄集體階段與 handoff，
`match-result.txt` 則保存完整比賽的時間、最後階段和勝方。

### 完成驗收與 500 seeds Batch

Cluster 現在真的會影響引擎，但不會控制玩家：同一 Cluster 內的威脅只會得到小幅注意力顯著度
加成；若目標持續失去 CoverNetwork，且局部獨自面對至少兩名敵人，攻擊理由可成為
`TARGET_ISOLATED_FROM_COVER_NETWORK`。每次機會都會凍結一份 `ClusterOpportunityEvidence`，
保存局部雙方人數、孤立信心、最低 ETA、是否 contested 和原因。孤立者只會獲得輕微返回隊形
的移動修正，不會被指定座標或鎖定目標。

Batch 找到的最後一個停滯，是兩名隊友用相同高度 Hold 同一名正在格擋的敵人。Hold 仍然沒有
最長時間；但若兩條真實 Hold 線同時指向同一目標與同一高度，第一人維持威脅，第二人會明確
選擇相反高度。這會打開刺擊線或迫使對方執行上下雙重格擋，第三人繼續擔任掩護者。

```powershell
java -cp .\out Experimental3v3BatchTest 100
java -cp .\out Experimental3v3BatchTest 500
```

Seeds 1–500 最終結果：`500` 場完成、`0` timeout、`0` 無效位置、`0` 出界、`0` 決定性失敗。
Batch 觀察到 `305` 次持續孤立、`1553` 次 Cluster 攻擊出手、`247` 次對應命中、`394` 次
3v2、`314` 次 3v1，以及三種 handoff。

### 淘汰後畫面凍結修正——里程碑 2：交易式陣容轉換

`MODERN_3V3` 不再從多個分支直接重建戰鬥引擎。每個物理 tick 完成攻擊解析後，
`captureRoster()` 會建立 `RosterSnapshot`。它用藍、紅各三個 bit 的固定 mask 保存原始六名
Agent 的生存狀態，另存雙方存活人數；即使 handoff 後 `blueTeam` 或 `redTeam` 陣列縮短，mask
的身分位置也不會改變。

程式碼只透過以下兩個實體方法處理人數變化：

```text
requestRosterTransition(snapshot)
→ applyPendingRosterTransition()
```

`processedRoster` 是已經安裝到引擎的陣容，`pendingRosterTransition` 是本 tick 物理解析完成後
看到的最終陣容。若同一 tick 有兩人死亡，兩個死亡會合併在同一個 snapshot，不會先建立
3v2，再於同一 tick 建立 2v2。成功安裝後會立刻更新 `processedRoster` 與相容用的
`experimentalAliveMask`，下一個 tick 看到相同 mask 時直接返回。

每次轉換使用遞增的 `transitionId`，並保存兩筆 `TransitionTrace`：第一筆是
`applied=false` 的 REQUEST，第二筆是 `applied=true` 的 APPLIED。兩筆共用同一 ID，記錄 tick、
模擬時間、初始 scenario、前後 controller、前後 phase、前後 mask、存活人數與原因。資料只存
在有界的模擬診斷結構中，不會每一幀輸出到 Swing 所在的 console。

`transitionInProgress` 阻止遞迴進入；`transitionAppliedThisTick` 阻止同一 tick 套用第二次。
已成功使用過的 `TransitionKey` 若再次出現，程式會增加
`duplicateTransitionApplications` 並拋出明確錯誤，不會靜默重建。其他計數包含 request、
application、重複 request、遞迴嘗試及同 tick 多次嘗試。

```powershell
java -cp .\out Experimental3v3TransitionTest
java '-Djava.awt.headless=true' -cp .\out Experimental3v3SwingTransitionTest
java -cp .\out Experimental3v3BatchTest 500
```

轉換測試現為 A–J，新增驗證第一次死亡只套用一次、同 tick 雙死只產生一個最終 snapshot，
以及後續 1,000 ticks 不會重複套用。3v3 batch 仍為 `500/500` 完成且沒有 timeout 或決定性
失敗。500 場共產生 `1463` 次 request 與完全相同的 `1463` 次 application；重複 request、
重複 application、遞迴進入、同 tick 第二次套用全部為 `0`。2v2 的 28 項驗收也仍在
500 seeds 全數通過。

### 不可逆的目前控制器——里程碑 3

現在 `SimulationScenario` 與實際執行戰鬥的控制器是兩個不同概念。scenario 只記錄比賽從
`MODERN_3V3` 或 `CONTROLLED_3V3` 開始，之後不會改變；`activeCombatController` 則使用新的
`ActiveCombatController` enum，唯一決定下一個 tick 要執行哪一套引擎：

```text
GROUP_3V3 → GROUP_3V2 → GROUP_3V1
                    ↘ GROUP_2V2 → TWO_VS_ONE → DUEL → COMPLETE
```

實驗模式的更新入口改用 `switch (activeCombatController)`。`GROUP_3V3`、`GROUP_3V2`、
`GROUP_3V1` 才能呼叫三人集體引擎；`GROUP_2V2`、`TWO_VS_ONE`、`DUEL` 只能呼叫既有的
handoff 引擎。handoff 內判斷是否更新 group movement 或 flank 時，也改讀 controller，不再
用初始 scenario 或只靠 `experimentalGroupPhase` 猜測引擎所有權。

轉換交易會先關閉舊的 3v3 engagement，清除 `modernGroup3Engagement`，建立既有的 2v2、2v1
或 1v1 engagement，再安裝新 controller。`applyPendingRosterTransition()` 會比較實際安裝的
controller 與 snapshot 所要求的 controller；不一致就明確失敗，不會更新 `processedRoster`。
因為死亡不可逆，程式裡也沒有從 `GROUP_2V2`、`TWO_VS_ONE` 或 `DUEL` 返回三人引擎的路徑。

新增的 K 測試強制走過 2v2、2v1、1v1，確認初始 scenario 保持 3v3、新 controller 接收下一
tick，而且所有舊 controller 的 tick counter 立即停止增加。500 場自然比賽的 controller
執行次數為：

```text
GROUP_3V3=277675  GROUP_3V2=77612  GROUP_3V1=123060
GROUP_2V2=85589   TWO_VS_ONE=125222  DUEL=21114
```

`1463` 次陣容轉換仍全部一對一套用；重複、遞迴與同 tick 第二次套用都是 `0`。本里程碑沒有
調整任何移動、攻擊、格擋或戰術參數。

### 死亡參照的中央清除——里程碑 4

每次陣容交易在重建 controller 前，只呼叫一個清除入口：
`purgeDeadReferences(aliveAgents())`。`aliveAgents()` 以物件身分建立 Set，所有檢查都使用同一份
存活來源，不會一部分讀縮短後的 team array、另一部分仍讀原始六人 array。

對每名 Agent，清除程式會處理：

```text
target、plannedTarget、primaryMark、secondaryThreat
coveredBy、covering、各種 pressure/dezone/rear reference
tacticalThreat、group3DefensiveThreat、movementIntent.target
AttentionAllocation 的五個威脅欄位
MultiThreatAttention 與 opponentAwareness
pressureByOpponent、localThreatByOpponent
```

若複合物件的任何參與者已死亡，整個物件會失效，包括 `CoverAssessment`、
`AttackOpportunity`、`RevengeOpportunity`、`CounterCoverAssessment`、`WeaponThreatLane` 與
`LocalManeuver`。`Group3v3Engagement` 另外清除 active CoverLink、Cluster、isolation time、
凍結證據、TeamAttackPlan、revenge 與 counter-cover。

`TeamFormation3.members` 與 `CoverNetwork.members` 是固定 roster 身分，不是 AI 的 active target，
所以不修改陣列本身；但 formation 的 left/center/right role、可用 cover relation 與 active plan
絕不能指向死人。若 TeamManeuver 的 inner/outer 玩家死亡，程式不會刪掉整個 maneuver 或重新
抽亂數，而是用 `rebindGroup3Maneuver()` 保留 type、方向、起始時間與持續時間，再把角色綁到
存活 formation role。`viable=false` 且理由為 `dead_participant` 的 CoverLink 只是一次評估紀錄，
不算 active cover link，也不影響 audit。

controller 安裝完成後，`auditDeadReferences()` 建立 `DeadReferenceAudit`，分別計算：

```text
activeDeadTargets、deadAttentionReferences、deadCoverLinks
deadClusterMembers、deadFormationMembers、deadPlanMembers、details
```

任何計數不為零都會立刻讓轉換失敗並列出 details，避免錯誤狀態在後續 tick 被反覆修補。新增
L 測試會故意把同一名死者塞入 target、plan、cover、pressure、movement 與 attention，再驗證
所有 active reference 都被清除。

500 場結果為：`1463` 次 transition application、`1463` 次 purge、轉換後死亡參照 `0`、
`500/500` 完成、決定性失敗 `0`。Swing 測試與 2v2 的 28 項驗收仍然通過。

### 有界日誌、進度 watchdog 與 failsafe——里程碑 5

陣容轉換現在透過 `TransitionLogger` 記錄。每個 request 與 applied 結論各有一行，內容包含
transition ID、tick、模擬時間、前後 controller、phase、雙方 mask 與原因。logger 同時保存
最多 `256` 行與 `256` 個去重 key；超過上限時，最舊行與對應 key 會一起移除，所以文字 buffer
與去重 Set 都不會無限成長。

一般遊戲模式不啟用 console，因此不會從 Swing EDT 呼叫 `System.out.println()`；
`consoleLinesEmitted` 保持 `0`。`emitOnce(key, line)` 用 key 阻止同一錯誤反覆輸出，並分別統計：

```text
consoleLinesEmitted
duplicateConsoleLinesSuppressed
storedLinesDropped
```

M 測試連續送入同一錯誤 1,000 次，結果只保存一行並抑制 `999` 行；再加入 300 個不同 key 後，
buffer 仍正好是 256 行。原本的 tacticalLog 也改為 `ArrayDeque`，最多保留 `4096` 個事件，避免
長時間可視比賽持續增加記憶體。

每個可視模擬 tick 結束時，`ProgressSnapshot` 會複製 tick、simulationTime、目前 controller、
`RosterSnapshot`、六個 `Pos` 與六個 `CombatState`。watchdog 比較相鄰 snapshot，更新：

```text
ticksWithoutAnyPositionChange
ticksWithoutAnyStateChange
repeatedTransitionStateTicks
logicalFreezeReports
```

只有連續五秒模擬時間都沒有任何位置、狀態、controller 或 roster 變化，才是全域 logical
freeze。單一 Agent 的無限 Hold 不是 freeze，只要其他 Agent 仍在移動或狀態仍推進。N 測試讓
一名 Agent Hold 六秒，確認 watchdog 不會誤報。

failsafe 使用 `RosterRequestObservation` 保存最近 60 ticks 的請求。相同且尚未處理的 roster
被要求超過兩次時，模擬只會暫停一次，並建立 `TransitionFailsafeReport`。報告凍結 scenario、
controller、目前/processed/pending roster、最後 transition、最後死亡參照 audit、active
engagement 與簡化 stack。O 測試確認第四次相同請求不會建立第二份報告或第二行警告。

500 場自然測試結果：`maximumTicksWithoutPositionChange=0`、
`maximumTicksWithoutStateChange=2112`、`maximumRepeatedTransitionStateTicks=0`、
`logicalFreezeReports=0`、`transitionFailsafePauses=0`、`consoleLinesEmitted=0`。即使戰鬥狀態長時間
不變，只要角色位置仍持續變化，就不會被誤判成 freeze。

### 圖形重現模式與最終驗證——里程碑 6

以下指令會開啟真正的 `MapPanel`、`javax.swing.Timer`，而且使用和主選單 3v3 相同的建立流程：

```powershell
java -cp .\out Main debug-3v3-transition 41
```

`Main.parseArguments()` 會保留 `SimulationScenario.MODERN_3V3`，只在建立完成的 MapPanel 上呼叫
`enableTransitionDebug()`。因此 debug 不會使用另一套 headless update loop，也不會改變 AI、
速度或物理。第一次死亡出現時，`TransitionDebugRecorder` 保存 wall-clock 與 roster，接著在
1× 下繼續觀察 15 秒。每個 Timer callback 都比較固定六名 Agent 的 `Pos` 與 `CombatState`，
統計位置變化、狀態變化、新攻擊與後續死亡；即使比賽提前 COMPLETE，Timer 與 repaint 計數仍
會繼續到觀察期結束。

報告內容先在 EDT 內組成不可變字串，再由 `CompletableFuture` 非同步寫入 UTF-8，避免檔案 I/O
拉長 Swing callback。輸出路徑為：

```text
out/debug/transition-seed-<seed>.log
```

報告包含 post-death progression、REQUEST/APPLIED transition、交易計數、死亡參照 audit、
watchdog、勝負結果與完整 EDT metrics。`Debug3v3TransitionSeedsTest` 會平行建立三個真正的
MapPanel，在 1× 執行 Timer，並定期把每個 panel 畫進 BufferedImage，確認不只是 simulation
callback，而是實際 paint path 也能持續執行。

```powershell
java '-Djava.awt.headless=true' -cp .\out Debug3v3TransitionSeedsTest
```

本次結果：

```text
seed 41  ：891 post-death ticks、396 次位置變化、57 次狀態變化、
           12 次攻擊、2 次後續死亡、EDT max 14.590 ms、比賽完成
seed 73  ：888 post-death ticks、765 次位置變化、77 次狀態變化、
           15 次攻擊、2 次後續死亡、EDT max 4.984 ms、比賽完成
seed 119 ：891 post-death ticks、510 次位置變化、47 次狀態變化、
           9 次攻擊、3 次後續死亡、EDT max 4.711 ms、比賽完成
```

三個 seed 的 `duplicateTransitionApplications`、`recursiveTransitionAttempts`、
`deadReferencesAfterTransition`、`logicalFreezeReports`、`edtTicksOver50Ms`、
`consoleLinesEmitted` 全部為 `0`。產生的三份報告可直接在 `out/debug` 檢查。

### 3v3 動態接近——新里程碑 1：資料模型與持續性

新接近系統先加入決策層，這一階段尚未修改速度或攻擊。`Group3ContactBand` 把距離狀態分成
`FAR_APPROACH`、`SPACE_CONTEST`、`CONTACT`、`ACTIVE_EXCHANGE`。判斷會讀雙方 formation center
距離、最近敵我距離、潛在接觸數，以及是否已有 attack/block 實體狀態。進入與離開門檻不同，
形成 hysteresis；若突然出現真實近距離，FAR 可以直接進 CONTACT，不必多停一個 tick。

`Group3v3Engagement` 現在為藍紅各保存一份 `Group3ApproachPlan`。物件內含：

```text
ApproachPattern
minimum/preferred/maximum duration
lead/support/trailing FormationRole3
lateralSign
forward/lateral/depth commitment
initialTeamCenter
predictedMeetingPoint
primaryReason
```

`predictedMeetingPoint` 先取雙方中心中點，再沿兩隊連線的垂直向量加入 `0.45–1.30 m` 的 lateral
offset，最後用 arena 邊界修正。LEFT/RIGHT lead、sweep、oblique press、center delayed 與
counter-rotate 都可被選到，沒有一般用途的直線模式。

選擇亂數不是主戰鬥 `Random`。程式以 match seed、Team 與 approach sequence 混合出獨立 Random，
因此不會因為新增 approach plan 而改變後面的 stab/block 抽樣順序。同 seed 的 pattern、role、
duration、meeting point 完全一致，但藍紅使用不同混合常數，不會被迫鏡像。plan 最短維持
`1.2–1.8 s`、偏好 `2.0–3.4 s`、最長 `4.0–5.0 s`；只有可信 weapon threat、formation break、
邊界危險或 durable isolation 可以提前中止。

`Group3ApproachPlanTest` 的 A–E 測試涵蓋 lateral meeting point、三個不同暫時角色、跨越快速
perception interval 的持續性、決定性、雙方獨立選擇、contact-band hysteresis，以及第一接觸
不建立 DUEL。100 seeds 3v3 batch 與 2v2 的 28 項驗收全部通過。本里程碑只建立可測試的控制
資料；下一里程碑才會把 plan 組合進 steering，因此目前不宣稱畫面軌跡已經改變。

### 3v3 動態接近——里程碑 2：轉向合成、速度與縱深

持續性的接近計畫現在會在 `FAR_APPROACH` 與 `SPACE_CONTEST` 期間真正影響 Agent 移動。
`group3ApproachVelocity()` 會把計畫類型、前進／橫向投入程度、暫時角色，以及預測交會點，
轉換為編隊座標中的速度向量。程式不會直接指定或瞬間改寫角色位置；結果仍會經過
`integrateVelocity()`，因此步法造成的加速度、慣性、最高速度、後退減速、碰撞與場地邊界
全部仍然有效。

在遠距接近時，朝敵方的速度分量限制為 `0.78–1.05 m/s`，橫向分量為
`0.28–0.58 m/s`。進入 `SPACE_CONTEST` 後，前進分量逐漸降為 `0.42–0.78 m/s`，橫向分量
提高為 `0.42–0.72 m/s`。不同 `ApproachPattern` 只調整這兩個分量：掃掠增加橫移、反向旋轉
犧牲部分前進速度、中央延遲稍微放慢接近。角色目前與 `predictedMeetingPoint` 的橫向距離也會
調整橫移強度，但不會每個 tick 反轉計畫方向。

暫時角色會形成柔性的前後層次。前導者的前進倍率是 `1.12`、支援者是 `1.00`、後隨者是
`0.88`，三者平均仍維持團隊速度目標。縱深修正分別朝
`+depthCommitment`、`+0.22 × depthCommitment`、`-0.68 × depthCommitment` 前進。這些數字只是
有上限的 steering 偏向，不是強制座標；武器威脅、閃避、碰撞和邊界仍可使隊形自然變形。

程式碼以 `Group3SteeringComposition` 明確保存每個來源：

```text
desiredVelocity = approachContribution
                + teamManeuverContribution
                + localManeuverContribution
                + formationContribution
                + coverContribution
                + avoidanceContribution
+ boundaryContribution
```

3v3 的隊友閃避會明確巡覽另外兩名仍存活的隊友，沒有沿用只認識一名隊友的 2v2 快捷邏輯。

`FAR_APPROACH` 中，團隊機動／隊形／掩護修正的權重是 `0.18 / 0.32 / 0.28`；到
`SPACE_CONTEST` 時提高為 `0.42 / 0.65 / 0.62`。一旦進入 `CONTACT`，或接近計畫因真實緊急
狀況而取消，權重立即恢復 `1.0`，所以迫近的刺刀與真正破裂的隊形仍享有最高優先權。
相反地，遠距離的 `RECOVER_FORMATION` 和 `COMPRESS` 門檻已放寬，暫時缺少一條掩護連線
不會再把整隊立刻拉回中央。

`Group3v3Metrics` 會累加七種 contribution 的長度，能直接判斷遠距離時究竟是哪一套系統
主導移動。`Group3ApproachSteeringTest` 的 A–G 測試涵蓋前進與橫移、前導者和後隨者的縱深、
接近 contribution 的主導性、接觸時完整交還舊移動系統、同 seed 軌跡完全一致、實際速度
落在指定區間，以及遠距掩護缺口不會誤觸恢復隊形。攻擊門檻、攻擊頻率、`TWO_VS_ONE`、
2v2 與人數轉換系統都沒有修改。

### 3v3 第一接觸——里程碑 3：事件紀錄與平直前線稽核

程式現在會區分整體進入 `CONTACT` 距離帶，以及每一名 Agent 自己的第一次實體接觸。
角色完成移動後，`measureGroup3FirstContacts()` 會尋找最近的存活敵人；實際距離小於或等於
`2.95 m` 時，建立不可變的 `FirstContactEvent`，保存 Agent、當時最近的敵人、模擬時間、
`FormationRole3`、真實距離和是否具有足夠實體掩護。每名 Agent 只會建立一次，因此之後換
目標不會改寫第一接觸的歷史證據。

時間差使用以下公式：

```text
firstContactSpread = 最後一名 Agent 的第一接觸時間
                   - 最早的第一接觸時間
```

另一個 `0.12 s` 的滑動視窗會計算同時發生的第一接觸最大數量，分別更新
`simultaneousThreePlayerContacts` 與 `simultaneousSixPlayerContacts`。這只是觀測資料，不會為了
製造錯開效果而強迫任何 Agent 等待。

第一個事件出現以前，`measureGroup3PreContactGeometry()` 會累加雙方中心的前進和橫向位移、
平均寬度、平均與最大縱深、相對初始中線的偏移，以及雙方路徑相關性。因為兩隊朝相反方向
接近，所以相關性定義為：

```text
teamPathCorrelation = -dot(normalize(blueDelta), normalize(redDelta))
```

數值接近 `1` 代表世界座標方向相反，但兩隊軌跡幾乎完全鏡像。程式也分開保存抵達
`SPACE_CONTEST`、第一次接觸和第一次攻擊所需的時間。

`FlatFrontConvergenceAudit` 會在六人都有事件、最早事件發生 `1.50 s` 後，或淘汰即將引發
人數轉換以前完成。它組合六項訊號：

```text
橫向位移 < 0.55 m
平均縱深 < 0.28 m
至少四個接觸集中在 0.18 s 內
路徑相關性 > 0.88
平均中線偏移 < 0.32 m
接近時間 > 4.20 s
```

至少三項成立才會標記為可疑。`reason` 仍會保留所有成立的原因，即使總數不足三項。
這個 audit 不會修改目標、計畫、速度或攻擊決策，只用來找出畫面表現平淡的 seed。

`Group3FirstContactAuditTest` 驗證翼側—中央—後線依序接觸、六人平直同步接觸、始終維持單一
`GROUP_3V3` 而不建立 `DUEL`，以及完全決定性。100 場測試結果為：
`approachAudits=100`、`flatFrontConvergences=1`、`simultaneousSixPlayerContacts=15`、平均接觸
時間差 `0.669 s`、平均接觸前橫移 `0.787 m`，且沒有 timeout、freeze、越界、死亡引用或
決定性失敗。

### 3v3 接近畫面驗證——里程碑 4

`ApproachVisualValidationCapture` 會以 `1/120 s` 的物理步長重播 seed `41`、`73`、`119`，也就是
遊戲 `1×` 速度使用的模擬時間。每個 seed 執行兩次：一次使用目前正式接近系統，另一次只在
畫面驗證中關閉 `Group3ApproachPlan` 的 movement contribution，重建里程碑以前的 baseline。
這個開關不會出現在遊戲介面，也沒有重新啟用 `FRONT_LINE` 或舊 controller；雙方仍使用相同
seed、Agent、攻擊和碰撞系統。

可重現指令：

```powershell
java '-Djava.awt.headless=true' -cp .\out ApproachVisualValidationTest
```

每個版本會在 `0.00`、`0.40`、`0.80`、`1.20`、`1.60`、`2.00`、`2.40`、`2.80 s` 擷取八個
畫面，標示距離帶、個別第一接觸數量、隊形折線、身體、刺刀方向和基本武器狀態。軌跡則每
`0.05 s` 取樣一次。輸出位置：

```text
out/approach-validation-3v3/seed-<seed>/
```

每個資料夾包含 `old-contact-sheet.png`、`new-contact-sheet.png`、新舊軌跡、兩張合併比較圖，
以及 `approach.txt`、`first-contact.txt`、`flat-front-audit.txt`。另外保留 `old-*` 與 `new-*`
文字報告，方便逐項比較。

實際結果：

| Seed | 第一次接觸 baseline → 新版 | Spread baseline → 新版 | 橫移 baseline → 新版 | 縱深 baseline → 新版 |
|---:|---:|---:|---:|---:|
| 41 | `3.467 → 1.608 s` | `0.092 → 0.192 s` | `0.101 → 0.774 m` | `0.009 → 0.382 m` |
| 73 | `3.475 → 1.658 s` | `0.083 → 0.475 s` | `0.086 → 0.914 m` | `0.015 → 0.476 m` |
| 119 | `3.467 → 1.367 s` | `0.092 → 1.183 s` | `0.103 → 0.682 m` | `0.009 → 0.201 m` |

三個 baseline 都被判定為平直，而且六名 Agent 在 `0.12 s` 視窗內同步接觸；三個新版都不是
可疑平直接近，也沒有六人同步接觸。seed 119 的平均縱深較小，但接觸時間差最大，而且畫面仍
有清楚曲線。因此本里程碑選擇保留里程碑 2 的參數；只為 seed 119 繼續提高縱深，反而可能
使 seed 41、73 的隊形變得僵硬，並造成針對小樣本的過度調整。

`ApproachVisualValidationTest` 會檢查所有檔案與完整解析度，並要求每個 seed 都必須更快接觸、
具有更大的 spread、至少四倍橫移、至少八倍縱深，而且 audit 必須從平直轉為非可疑。

### 3v3 接近系統最終驗收——里程碑 5

最後一個里程碑沒有再加入新的 AI 行為，而是固定里程碑 4 選定的參數，並以遊戲實際程式碼
執行完整驗證。可重現順序如下：

```powershell
javac -encoding UTF-8 -d .\out .\src\*.java
java '-Djava.awt.headless=true' -cp .\out ApproachVisualValidationTest
java -cp .\out Group3FirstContactAuditTest
java -cp .\out Group3ApproachSteeringTest
java -cp .\out Group3ApproachPlanTest
java -cp .\out Experimental3v3TransitionTest
java -cp .\out Experimental3v3BatchTest 100
java -cp .\out Experimental3v3BatchTest 500
java -cp .\out FinalAcceptanceTest 500
```

500 場 3v3 結果：

```text
matchesCompleted=500
timeouts=0
blueWins=254
redWins=246
transitionRequests=1577
transitionApplications=1577
deadReferencesAfterTransition=0
logicalFreezeReports=0
transitionFailsafePauses=0
approachAudits=500
flatFrontConvergences=1
simultaneousSixPlayerContacts=49
averageFirstContactSpread=0.738 s
averagePreContactLateralDistance=0.787 m
averagePreContactDepth=0.305 m
averageTimeToFirstContact=1.497 s
averageTimeToFirstAttack=4.482 s
invalidPositions=0
arenaExits=0
determinismFailures=0
consoleLinesEmitted=0
```

500 場 2v2 也完成 `500/500`，沒有 timeout、stalemate、無效位置、越界、thrashing 或決定性
失敗。`FinalAcceptanceTest` 的 28 項全部通過，包含第一名死亡以前保持 groupfight、死亡後實體
交接至 `TWO_VS_ONE`、完整死亡因果鏈，以及舊系統啟用次數為零。

接近系統 28 項需求和程式證據如下：

1. Watchdog 沒有誤報：`logicalFreezeReports=0`。
2. 正常模式沒有 console 洗版：`consoleLinesEmitted=0`。
3. 轉換維持交易式：`1577` 次要求對應 `1577` 次套用。
4. 2v2 通過 500 場完整驗收。
5. `TWO_VS_ONE` 的 B–F 測試保持通過。
6. 第一次接觸平均為 `1.497 s`。
7. 接觸前平均橫移為 `0.787 m`。
8. 藍紅使用彼此獨立的衍生 RNG。
9. 暫時角色允許一側成為前導。
10. 接觸前平均縱深為 `0.305 m`。
11. 500 場中只有一場被 audit 判為平直。
12. 只有 49 場的六名 Agent 在 `0.12 s` 視窗內同步接觸。
13. 遠距掩護缺口不會立刻強制恢復隊形。
14. 真實緊急狀況取消 plan 後，所有實體防禦權重恢復為 `1.0`。
15. 有限注意力及其延遲沒有修改。
16. 攻擊仍必須經過 `AttackOpportunity`。
17. 接近 plan 本身不會呼叫刺擊。
18. Revenge 沒有重新校準。
19. Counter-cover 沒有重新校準。
20. 傷害、武器碰撞與格擋仍使用實體判定。
21. Cluster 仍然只是描述局部幾何。
22. 第一接觸測試確認不會建立固定 `DUEL`。
23. 沒有針對 seed 寫 AI；41、73、119 只作為畫面捕捉輸入。
24. Plan 具有持續時間，而且測試沒有發現 thrashing。
25. 100 與 500 場 batch 的決定性重播全部通過。
26. 死亡後的交易式轉換與圖形比賽仍會繼續。
27. 3v3 和 2v2 驗收的 100、500 場測試皆通過。
28. 法文與中文 README 都詳細說明資料模型、公式、測試和結果。

接近系統開發期間沒有修改被凍結的轉換系統、`GROUP_2V2`、`TWO_VS_ONE`、攻擊門檻、攻擊
時間、格擋、傷害、revenge 或 counter-cover。唯一的替代路徑是畫面捕捉用 baseline；它預設
關閉，正常遊戲和 UI 都無法啟用。
