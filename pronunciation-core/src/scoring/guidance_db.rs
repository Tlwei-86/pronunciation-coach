//! Knowledge base of 44 American English phonemes embodied articulatory guidance (Chinese).
//! Formulations and descriptions strictly adhere to RFC Section 7.1 & 7.2 and Section 6.3.

use serde::{Deserialize, Serialize};

/// Detailed 5-organ articulatory guidance view for UI display.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct OrganGuidanceView {
    /// 唇部动作要领
    pub lips: String,
    /// 牙齿动作要领
    pub teeth: String,
    /// 舌位动作要领
    pub tongue: String,
    /// 气流动作要领
    pub airflow: String,
    /// 声带振动状态
    pub vocal_cords: String,
    /// 一句话动作口诀
    pub summary: String,
    /// 典型偏误场景
    pub typical_error: String,
    /// 针对性纠偏动作口诀
    pub correction_tip: String,
}

/// Static entry defining articulatory guidance and dynamic visual weight for a phoneme.
#[derive(Debug, Clone, PartialEq)]
pub struct ArticulatoryGuidanceEntry {
    pub ipa: &'static str,
    pub category: &'static str,
    pub lips: &'static str,
    pub teeth: &'static str,
    pub tongue: &'static str,
    pub airflow: &'static str,
    pub vocal_cords: &'static str,
    pub summary: &'static str,
    pub typical_error: &'static str,
    pub correction_tip: &'static str,
    pub visual_weight: f32,
}

impl ArticulatoryGuidanceEntry {
    pub fn to_view(&self) -> OrganGuidanceView {
        OrganGuidanceView {
            lips: self.lips.to_string(),
            teeth: self.teeth.to_string(),
            tongue: self.tongue.to_string(),
            airflow: self.airflow.to_string(),
            vocal_cords: self.vocal_cords.to_string(),
            summary: self.summary.to_string(),
            typical_error: self.typical_error.to_string(),
            correction_tip: self.correction_tip.to_string(),
        }
    }
}

/// Static catalog of all 44 American English phonemes embodied Chinese guidance.
pub static PHONEME_GUIDANCE_CATALOG: &[ArticulatoryGuidanceEntry] = &[
    // --- 20 Vowels (RFC Section 7.1) ---
    ArticulatoryGuidanceEntry {
        ipa: "iː",
        category: "前高长元音",
        lips: "嘴角向两侧用力拉开呈微笑状",
        teeth: "上下门牙微启，间隙极窄",
        tongue: "舌前部高高拱起，尽量贴近上硬腭（高舌位）",
        airflow: "气流从舌面与硬腭微小缝隙平稳流出",
        vocal_cords: "声带持续振动发饱满长音",
        summary:
            "嘴角向两侧用力拉开呈微笑状，舌前部尽量贴近上硬腭，上下齿微启，声带持续振动发长音。",
        typical_error: "若读成松散的 /ɪ/",
        correction_tip: "嘴角没有拉开！请像拍照微笑一样收紧嘴角，舌面用力抬到最高处。",
        visual_weight: 0.25,
    },
    ArticulatoryGuidanceEntry {
        ipa: "ɪ",
        category: "前半高短元音",
        lips: "嘴角自然放松不紧绷",
        teeth: "下颌微落约一指宽",
        tongue: "舌前部略高于中部但不碰硬腭，舌位适中",
        airflow: "气流短促轻快流出",
        vocal_cords: "声带短促利落振动",
        summary: "嘴角自然放松不紧绷，下颌微落约一指宽，舌前部略高于中部但不碰硬腭，声音短促轻快。",
        typical_error: "若读成紧绷的 /iː/",
        correction_tip: "嘴唇收得太紧了。请完全放松嘴角肌肉，下巴微松落下约一指宽。",
        visual_weight: 0.15,
    },
    ArticulatoryGuidanceEntry {
        ipa: "e",
        category: "前中元音",
        lips: "唇形扁平自然展开",
        teeth: "下巴下落约一指半宽",
        tongue: "舌前部稍稍抬起，舌尖轻触下齿龈",
        airflow: "气流顺畅呼出",
        vocal_cords: "声带振动有力",
        summary: "唇形扁平自然展开，下巴下落约一指半宽，舌前部稍抬起，声带振动有力。",
        typical_error: "若读成 /æ/",
        correction_tip: "嘴张太大了。请将下巴向上微收，保持嘴角平展，不要向下过度掉下巴。",
        visual_weight: 0.20,
    },
    ArticulatoryGuidanceEntry {
        ipa: "æ",
        category: "前低开元音",
        lips: "嘴角向两侧拉开，双唇充分展开",
        teeth: "下巴明显向下大开（约两指宽）",
        tongue: "舌尖抵住下门牙内侧，舌身压平沉底",
        airflow: "气流自喉腔顺畅大开口冲出",
        vocal_cords: "声带振动共鸣",
        summary: "下巴明显向下大开（约两指宽），嘴角继续向两侧拉开，舌尖抵住下门牙内侧，舌身压平。",
        typical_error: "若读成 /e/",
        correction_tip: "口型开度不够。请将下巴充分向下沉开，嘴角保持拉展，舌尖抵紧下门牙。",
        visual_weight: 0.25,
    },
    ArticulatoryGuidanceEntry {
        ipa: "ʌ",
        category: "央半低元音",
        lips: "嘴唇完全放松不圆撮",
        teeth: "下颌适度放松微开约一指宽",
        tongue: "舌身居中自然平放，舌尖微离下齿背",
        airflow: "短促爆发释放",
        vocal_cords: "声带短促振动发力",
        summary: "下颌适度放松微开约一指宽，嘴唇完全放松不圆撮，舌身居中自然平放，短促发力。",
        typical_error: "若读成大口 /ɑː/",
        correction_tip: "下巴掉得太低了。请轻轻合上一些，不要撮唇，舌头平平地放在口腔中间。",
        visual_weight: 0.15,
    },
    ArticulatoryGuidanceEntry {
        ipa: "ɜː",
        category: "央中长元音",
        lips: "双唇微向外凸出但略圆",
        teeth: "下颌微开一指宽",
        tongue: "舌身中部隆起悬空，舌侧轻微接触上槽牙",
        airflow: "气流平缓通过拱起舌身",
        vocal_cords: "声带平稳拉长振动",
        summary: "嘴唇微向外凸出但略圆，下颌微开，舌身中部隆起悬空，舌侧轻微接触上槽牙，平稳长音。",
        typical_error: "若卷舌过度或发扁",
        correction_tip: "不要过早后卷舌尖，保持舌身正中央向上拱起，声带平稳拉长发音。",
        visual_weight: 0.20,
    },
    ArticulatoryGuidanceEntry {
        ipa: "ə",
        category: "弱化央元音",
        lips: "全发音系统处于极致放松状态，嘴唇微张",
        teeth: "下颌自然放松微垂",
        tongue: "舌身完全摊平不用力",
        airflow: "微弱气流一掠而过",
        vocal_cords: "声带极轻微振动",
        summary: "全发音系统处于极致放松状态，嘴唇微张，舌身完全摊平不用力，极短、极轻的一带而过。",
        typical_error: "若发音过重",
        correction_tip: "用力过猛了。这是最放松的弱读音，像叹气一样轻轻咕嘟一声即可。",
        visual_weight: 0.10,
    },
    ArticulatoryGuidanceEntry {
        ipa: "uː",
        category: "后高长元音",
        lips: "双唇收至最小圆形并明显向前撅起撮紧",
        teeth: "下巴极窄，仅留缝隙",
        tongue: "舌后部向软腭高高隆起，舌尖微缩",
        airflow: "气流从紧圆小孔呼出",
        vocal_cords: "声带持续振动发长音",
        summary: "双唇收至最小圆形并明显向前撅起，下巴极窄，舌后部向软腭高高隆起，长音。",
        typical_error: "若嘴唇不圆",
        correction_tip: "嘴唇撅得不够圆！请将双唇紧缩成吸管口大小向前凸起，舌根用力向后上方提起。",
        visual_weight: 0.35,
    },
    ArticulatoryGuidanceEntry {
        ipa: "ʊ",
        category: "后半高短元音",
        lips: "双唇自然收圆但肌肉不紧绷",
        teeth: "下巴开度略大于 /uː/",
        tongue: "舌后部微抬，舌身后缩",
        airflow: "短促平缓释放",
        vocal_cords: "声带短促振动",
        summary: "双唇自然收圆但肌肉不紧绷，开度略大于 /uː/，舌后部微抬，发音短促。",
        typical_error: "若读成紧圆 /uː/",
        correction_tip: "嘴唇别使劲撅。保持微微圆唇即可，下巴稍微放松，发音短促利落。",
        visual_weight: 0.25,
    },
    ArticulatoryGuidanceEntry {
        ipa: "ɔː",
        category: "后半低长元音",
        lips: "嘴唇收成中等椭圆形并向前微凸",
        teeth: "下颌下落约两指宽",
        tongue: "舌后部下压并向后咽壁收缩",
        airflow: "气流自深咽喉部通畅涌出",
        vocal_cords: "声带深沉饱满共鸣",
        summary: "嘴唇收成中等椭圆形并向前微凸，下颌下落两指宽，舌后部下压后缩，饱满深沉。",
        typical_error: "若发成扁平音",
        correction_tip: "嘴唇必须呈 O 型向前噘起，舌头往后缩，声音要从喉咙深处共鸣发出。",
        visual_weight: 0.25,
    },
    ArticulatoryGuidanceEntry {
        ipa: "ɑː",
        category: "后低开长元音",
        lips: "嘴唇呈自然不圆形态充分展开",
        teeth: "口腔开度达到最大（如同看牙医张大口）",
        tongue: "舌身整体降至口腔底部，舌面平坦后缩",
        airflow: "宽阔气流毫无遮挡呼出",
        vocal_cords: "声带开阔长久振动",
        summary: "口腔开度达到最大（如同医生看扁桃体），嘴唇呈自然不圆形态，舌身整体降至口腔底部。",
        typical_error: "若嘴没张开",
        correction_tip: "嘴巴张得太小了。请彻底打开下巴，舌头平平沉在口底，像看牙医一样‘啊’出来。",
        visual_weight: 0.25,
    },
    ArticulatoryGuidanceEntry {
        ipa: "ɒ",
        category: "后低短元音",
        lips: "嘴唇呈稍扁的圆形",
        teeth: "下颌充分打开两指宽",
        tongue: "舌根后缩居低位",
        airflow: "急促短促流出",
        vocal_cords: "声带急促利落振动",
        summary: "下颌充分打开，嘴唇呈稍扁的圆形，舌根后缩，短促利落。",
        typical_error: "若发成长音",
        correction_tip: "这是急促短音，口型圆而下沉，听到声音立刻收住，不要拖尾。",
        visual_weight: 0.25,
    },
    ArticulatoryGuidanceEntry {
        ipa: "eɪ",
        category: "合口双元音",
        lips: "由半开扁唇迅速向更扁微笑滑动",
        teeth: "下巴由开向闭滑动，嘴型收窄",
        tongue: "舌面前部由中位向上平滑抬起至高位",
        airflow: "连续平滑滑行",
        vocal_cords: "声带持续振动，前强后弱",
        summary: "从前中扁唇 /e/ 快速平滑滑动至高位 /ɪ/，下巴由开向闭滑动，嘴角同步向两侧拉宽。",
        typical_error: "若滑行不完整",
        correction_tip: "不要只读一个音！必须有从开口到闭口拉唇的动作滑动过程。",
        visual_weight: 0.25,
    },
    ArticulatoryGuidanceEntry {
        ipa: "aɪ",
        category: "合口双元音",
        lips: "由极大开度迅速向两侧拉宽成扁平微笑",
        teeth: "下颌由完全大开迅速向上提拉闭合",
        tongue: "舌前部由口底平卧迅速向硬腭前部抬起",
        airflow: "大范围平滑过渡",
        vocal_cords: "声带持续振动",
        summary: "从大开口 /a/ 快速滑向窄口 /ɪ/，下颌由完全大开迅速向上提拉闭合。",
        typical_error: "若尾音缺失",
        correction_tip: "结尾声音发虚。请确保下颌向上提拉，舌面前部抬起接近上硬腭的位置。",
        visual_weight: 0.30,
    },
    ArticulatoryGuidanceEntry {
        ipa: "ɔɪ",
        category: "合口双元音",
        lips: "双唇必须由‘圆圈’迅速展开拉伸成‘扁平微笑’",
        teeth: "下巴由半开两指上收至一指",
        tongue: "舌后部隆起迅速切换为舌前部抬高",
        airflow: "气流连续过渡",
        vocal_cords: "声带持续振动",
        summary: "从圆唇中开口 /ɔː/ 滑向扁唇窄开口 /ɪ/，口型由圆向前滑变为展唇微笑。",
        typical_error: "若嘴型没变",
        correction_tip: "双唇必须由‘圆圈’迅速展开拉伸成‘扁平微笑’，完成口型蜕变。",
        visual_weight: 0.30,
    },
    ArticulatoryGuidanceEntry {
        ipa: "aʊ",
        category: "合口双元音",
        lips: "由自然大开迅速向外聚拢并向前撮成小圆圈",
        teeth: "下颌由大落迅速向上合拢",
        tongue: "舌身由大平沉快速滑向舌后部向软腭隆起",
        airflow: "宽阔气流收紧喷出",
        vocal_cords: "声带持续振动",
        summary: "从大开口 /a/ 滑向小圆唇 /ʊ/，下颌由大落迅速合拢，双唇同步收拢向前撮圆。",
        typical_error: "若嘴唇没有收圆",
        correction_tip: "发到结尾时嘴唇必须像吹口哨一样聚拢收圆，下巴同步合拢。",
        visual_weight: 0.30,
    },
    ArticulatoryGuidanceEntry {
        ipa: "oʊ",
        category: "合口双元音",
        lips: "嘴唇由微圆聚拢为小紧圆",
        teeth: "下颌由半开微向上提",
        tongue: "舌后部向后上方抬起",
        airflow: "圆唇通道内气流平滑流出",
        vocal_cords: "声带饱满振动",
        summary: "从半开中性圆唇快速滑向小圆唇，嘴唇由微圆聚拢为小紧圆，舌根向后上方抬起。",
        typical_error: "若只发单一元音",
        correction_tip: "美音的 /oʊ/ 是滑动的，嘴角由松到紧向前撮起成一个小圆圈。",
        visual_weight: 0.30,
    },
    ArticulatoryGuidanceEntry {
        ipa: "ɪə",
        category: "集中双元音",
        lips: "由微展扁唇向自然放松微开滑移",
        teeth: "下颌维持微开状态",
        tongue: "由前半高窄口迅速滑向极致放松的中央弱音位",
        airflow: "前重后轻流出",
        vocal_cords: "声带平稳振动",
        summary: "由前半高窄口 /ɪ/ 快速滑向极致放松的中央弱音 /ə/，下颌微开。",
        typical_error: "若滑动迟钝",
        correction_tip: "起点舌位抬高，迅速滑向放松的央位，尾音轻弱短促。",
        visual_weight: 0.20,
    },
    ArticulatoryGuidanceEntry {
        ipa: "eə",
        category: "集中双元音",
        lips: "由半开扁唇平展滑向中央自然中性唇形",
        teeth: "下颌下落一指半宽渐收",
        tongue: "舌前部适度抬起平滑向中央放松沉下",
        airflow: "平稳过渡流出",
        vocal_cords: "声带平稳振动",
        summary: "由扁唇平开口 /e/ 滑向中央弱音 /ə/，嘴角逐渐放松归位。",
        typical_error: "若开口过大",
        correction_tip: "起点保持半开扁唇，快速向正中位置放松即可。",
        visual_weight: 0.20,
    },
    ArticulatoryGuidanceEntry {
        ipa: "ʊə",
        category: "集中双元音",
        lips: "双唇由微圆向自然放松舒展开",
        teeth: "下颌由微合向放松微开",
        tongue: "舌后部微抬平滑向中央放松滑落",
        airflow: "气流平缓过渡",
        vocal_cords: "声带平稳振动",
        summary: "由小圆唇 /ʊ/ 滑向中央放松音 /ə/，双唇由微圆向自然放松舒展。",
        typical_error: "若嘴型锁定",
        correction_tip: "结尾嘴唇要完全舒展开，不要一直噘着嘴。",
        visual_weight: 0.25,
    },
    // --- 24 Consonants (RFC Section 7.2) ---
    ArticulatoryGuidanceEntry {
        ipa: "p",
        category: "清双唇塞音",
        lips: "上下唇紧紧闭合并积蓄气流，随后瞬间向外弹开",
        teeth: "牙齿自然微启，阻气完全在双唇",
        tongue: "舌头平放口底，不参与阻气",
        airflow: "双唇弹开瞬间爆发强力清脆气流冲出",
        vocal_cords: "声带完全不振动",
        summary: "上下唇紧紧闭合并积蓄气流，随后双唇瞬间向外弹开发出清脆爆破，声带完全不振动。",
        typical_error: "若双唇漏气无力",
        correction_tip: "嘴唇没有抿紧！发音前上下唇必须紧密闭合憋住气，然后用力弹开。",
        visual_weight: 0.35,
    },
    ArticulatoryGuidanceEntry {
        ipa: "b",
        category: "浊双唇塞音",
        lips: "上下唇紧密闭合阻断气流，闭气瞬间声带开始振动，随后双唇弹开发音",
        teeth: "牙齿自然微开",
        tongue: "舌身平放放松",
        airflow: "双唇爆发性释放浊气流",
        vocal_cords: "声带在双唇弹开前及弹开时强烈持续振动",
        summary: "上下唇紧密闭合阻断气流，闭气瞬间声带开始振动，随后双唇弹开发音。",
        typical_error: "若发成清音 /p/",
        correction_tip: "没有发出声带共鸣。在嘴唇弹开的一瞬间，喉咙声带必须用力震动。",
        visual_weight: 0.35,
    },
    ArticulatoryGuidanceEntry {
        ipa: "t",
        category: "清齿龈塞音",
        lips: "嘴唇自然微张配合后续音素",
        teeth: "牙齿微张",
        tongue: "舌尖紧紧抵住上排牙齿后面的牙龈处憋住气流，随后舌尖快速弹下释放强气流",
        airflow: "舌尖弹下瞬间爆发强劲无声高压气流",
        vocal_cords: "声带完全不振动",
        summary:
            "舌尖紧紧抵住上排牙齿后面的牙龈处憋住气流，然后舌尖快速弹下释放强气流，声带不振动。",
        typical_error: "若舌头碰牙齿",
        correction_tip: "舌头放错位置了！不要顶在牙齿上，要顶在牙齿上方凸起的牙龈肉上。",
        visual_weight: 0.15,
    },
    ArticulatoryGuidanceEntry {
        ipa: "d",
        category: "浊齿龈塞音",
        lips: "嘴唇自然微张",
        teeth: "牙齿微开",
        tongue: "舌尖紧抵上牙龈阻断气流，释放瞬间带动浊爆破",
        airflow: "带声带振动的浊爆破气流冲出",
        vocal_cords: "闭锁与释放全程伴随强烈声带振动",
        summary: "舌尖紧抵上牙龈，阻断气流的同时振动声带，舌尖弹下带出浊爆破音。",
        typical_error: "若声带不振动",
        correction_tip: "发音太轻成了 /t/。舌尖弹下的同时，手摸喉咙必须感觉到声带强烈振动。",
        visual_weight: 0.15,
    },
    ArticulatoryGuidanceEntry {
        ipa: "k",
        category: "清软腭塞音",
        lips: "嘴唇张开顺应后续音，不闭唇",
        teeth: "上下齿微开",
        tongue: "舌根用力向后上方抬起，紧贴口腔顶部的软腭憋气，随后舌根骤然落下冲出气流",
        airflow: "舌根松开瞬间爆发强气流",
        vocal_cords: "声带完全不振动",
        summary:
            "舌根用力向后上方抬起，紧贴口腔顶部的软腭憋气，随后舌根骤然落下冲出气流，声带不振。",
        typical_error: "若变成中音",
        correction_tip: "舌根没有封严。请将舌头后半段用力向上顶紧软腭，憋住气再爆破释放。",
        visual_weight: 0.05,
    },
    ArticulatoryGuidanceEntry {
        ipa: "g",
        category: "浊软腭塞音",
        lips: "嘴唇自然张开",
        teeth: "上下齿微开",
        tongue: "舌根上抬贴紧软腭阻断气流，在舌根落下释放前声带开始浊化振动",
        airflow: "浊化爆发气流从软腭释出",
        vocal_cords: "舌根闭气及弹开时声带持续低沉振动",
        summary: "舌根上抬贴紧软腭阻断气流，在舌根落下释放前，喉咙声带开始浊化振动。",
        typical_error: "若声带发空",
        correction_tip: "声音发飘。舌根顶紧软腭后，喉部先发出一声低沉的颤动，再向外冲破。",
        visual_weight: 0.05,
    },
    ArticulatoryGuidanceEntry {
        ipa: "f",
        category: "清唇齿擦音",
        lips: "上排门牙轻轻贴在下嘴唇内侧边缘，绝对不能双唇闭合",
        teeth: "上排切牙切入下唇内缘",
        tongue: "舌身放松平放口底",
        airflow: "气流从唇齿缝隙摩擦喷出",
        vocal_cords: "声带完全不振动",
        summary:
            "上排门牙轻轻贴在下嘴唇内侧边缘，气流从唇齿缝隙摩擦喷出，绝对不能双唇闭合，声带不振。",
        typical_error: "若上下唇碰在一起",
        correction_tip: "不要用嘴唇碰嘴唇！只能用上排门牙咬在下唇内侧，吹出丝丝摩擦气流。",
        visual_weight: 0.35,
    },
    ArticulatoryGuidanceEntry {
        ipa: "v",
        category: "浊唇齿擦音",
        lips: "上排门牙轻贴下唇内缘，强气流穿透缝隙",
        teeth: "上门牙轻触下唇内缘",
        tongue: "舌身平放",
        airflow: "摩擦气流强力挤出",
        vocal_cords: "声带持续强烈振动，下唇有明显麻酥感",
        summary: "上排门牙轻贴下唇内缘，在让气流强力穿过缝隙的同时声带持续震动，下唇有明显麻酥感。",
        typical_error: "若发成圆唇 /w/",
        correction_tip: "嘴唇过度向前撅起了！收回双唇，让上门牙贴住下唇并振动声带。",
        visual_weight: 0.35,
    },
    ArticulatoryGuidanceEntry {
        ipa: "θ",
        category: "清齿间擦音",
        lips: "嘴唇自然张开",
        teeth: "上下门牙微启",
        tongue: "舌尖向前轻轻伸出，置于上下门牙之间（不可咬死），气流经舌面与上齿缝隙柔和吹出",
        airflow: "柔和清气流穿过齿间缝隙",
        vocal_cords: "声带完全不振动",
        summary: "舌尖向前轻轻伸出置于上下门牙之间，气流经舌面与上齿缝隙柔和吹出，声带不振动。",
        typical_error: "若缩舌发成 /s/",
        correction_tip: "舌头缩在牙齿后面了！必须将舌尖探出上下牙齿之间，吹出柔和清气流。",
        visual_weight: 0.30,
    },
    ArticulatoryGuidanceEntry {
        ipa: "ð",
        category: "浊齿间擦音",
        lips: "嘴唇放松微张",
        teeth: "上下门牙微张",
        tongue: "舌尖伸出上下门牙之间，气流穿过缝隙，舌尖能感受到明显震颤麻感",
        airflow: "伴随震颤的摩擦气流",
        vocal_cords: "声带持续振动",
        summary: "舌尖伸出上下门牙之间，气流穿过缝隙的同时声带持续振动，舌尖能感受到明显震颤麻感。",
        typical_error: "若缩舌发成 /z/ 或 /d/",
        correction_tip: "不要直接用舌头顶牙齿。舌尖一定要伸出来夹在门牙间，并震动声带。",
        visual_weight: 0.30,
    },
    ArticulatoryGuidanceEntry {
        ipa: "s",
        category: "清齿龈擦音",
        lips: "嘴角微向两侧展开展平",
        teeth: "上下门牙轻合",
        tongue: "舌尖抬至上牙龈正后方但不接触，形成极狭窄缝隙，喷出高频尖锐气流",
        airflow: "集中喷出 5kHz~8kHz 尖锐高频嘶嘶气流",
        vocal_cords: "声带完全不振动",
        summary: "上下门牙轻合，舌尖抬至上牙龈正后方形成极狭窄缝隙，喷出高频尖锐嘶嘶气流。",
        typical_error: "若漏气松散发成 /θ/",
        correction_tip: "不要伸舌头！上下牙轻轻咬拢，舌尖藏在牙龈后，集中喷出高频啸叫声。",
        visual_weight: 0.15,
    },
    ArticulatoryGuidanceEntry {
        ipa: "z",
        category: "浊齿龈擦音",
        lips: "嘴角微向两侧展平",
        teeth: "上下门牙轻合留微缝",
        tongue: "口型与舌位与 /s/ 完全一致",
        airflow: "高频气流与声带共鸣共同挤出",
        vocal_cords: "声带持续强烈振动（如蜜蜂嗡嗡声）",
        summary:
            "口型与舌位与 /s/ 完全一致，但在喷出高频气流的同时声带持续强烈振动（如蜜蜂嗡嗡声）。",
        typical_error: "若词尾清化无声",
        correction_tip: "声带偷懒没有振动。牙齿咬住缝隙，喉咙持续发出像蜜蜂一样的嗡嗡震动。",
        visual_weight: 0.15,
    },
    ArticulatoryGuidanceEntry {
        ipa: "ʃ",
        category: "清后齿龈擦音",
        lips: "双唇向前微噘呈喇叭状",
        teeth: "牙齿近合",
        tongue: "舌身宽宽抬起靠近硬腭后部，让大量气流宽屏摩擦冲出（嘘声）",
        airflow: "宽阔中高频紊流气流",
        vocal_cords: "声带完全不振动",
        summary: "双唇向前微噘呈喇叭状，舌身宽宽抬起靠近硬腭后部，让大量气流宽屏摩擦冲出（嘘声）。",
        typical_error: "若嘴角咧开发成 /s/",
        correction_tip: "嘴唇没有撅起！嘴唇必须向前微凸成喇叭口，舌头往后收，发出‘嘘’的摩擦音。",
        visual_weight: 0.25,
    },
    ArticulatoryGuidanceEntry {
        ipa: "ʒ",
        category: "浊后齿龈擦音",
        lips: "双唇向前微撅呈喇叭口",
        teeth: "上下齿微合",
        tongue: "舌身抬至硬腭后部",
        airflow: "气流平稳摩擦涌出",
        vocal_cords: "喉部声带平稳振动",
        summary: "双唇向前微撅呈喇叭口，舌身抬至硬腭后部，气流冲出摩擦的同时喉部声带平稳振动。",
        typical_error: "若声带无振动",
        correction_tip: "发成了清音 /ʃ/。保持喇叭口唇型，手摸喉咙必须能感受到平稳的声带颤动。",
        visual_weight: 0.25,
    },
    ArticulatoryGuidanceEntry {
        ipa: "h",
        category: "清声门擦音",
        lips: "口腔完全顺应后续元音的口型打开",
        teeth: "牙齿自然张开",
        tongue: "舌身完全顺应后续元音舌位",
        airflow: "声门微开，仅凭肺部呼出一口微弱温暖的气流",
        vocal_cords: "声带完全不振动，声门摩擦",
        summary: "口腔顺应后续元音打开，声门微开，仅凭肺部呼出一口微弱温暖的无声气流。",
        typical_error: "若喉咙摩擦过重",
        correction_tip: "不要喉部使劲咯痰！就像冬天哈气暖手一样，轻松呼出一口无声的气流。",
        visual_weight: 0.05,
    },
    ArticulatoryGuidanceEntry {
        ipa: "tʃ",
        category: "清塞擦音",
        lips: "双唇向前微噘",
        teeth: "牙齿近合",
        tongue: "前塞后擦：舌尖先抵住上牙龈憋气，随后舌尖突然松开转化为 /ʃ/ 的摩擦",
        airflow: "闭塞瞬间爆发紧接摩擦喷出",
        vocal_cords: "声带完全不振动",
        summary: "前塞后擦：舌尖先抵住上牙龈憋气，双唇向前微噘，随后舌尖突然松开转化为摩擦喷气。",
        typical_error: "若只有摩擦无爆破",
        correction_tip: "少了前面的爆破阻断。发音起点必须舌尖顶死上牙龈憋住气，然后迅速冲开。",
        visual_weight: 0.25,
    },
    ArticulatoryGuidanceEntry {
        ipa: "dʒ",
        category: "浊塞擦音",
        lips: "双唇微噘成喇叭口",
        teeth: "牙齿微合",
        tongue: "舌尖顶上牙龈憋气，随后释放转化为浊摩擦",
        airflow: "强爆破与强浊摩擦结合",
        vocal_cords: "全程保持声带强烈振动",
        summary: "舌尖顶上牙龈憋气，双唇微噘，舌尖释放转化为浊摩擦的同时全程保持声带强烈振动。",
        typical_error: "若发成轻清音",
        correction_tip: "声音发脆发干。舌尖顶住爆破和松开摩擦的全过程，声带都要持续发出轰鸣。",
        visual_weight: 0.25,
    },
    ArticulatoryGuidanceEntry {
        ipa: "m",
        category: "浊双唇鼻音",
        lips: "上下唇紧紧闭合，阻断口腔通道",
        teeth: "牙齿自然微启",
        tongue: "舌身平放，不阻碍口腔后部",
        airflow: "气流完全从鼻腔振动涌出，口内完全封闭",
        vocal_cords: "声带持续振动",
        summary: "上下唇紧紧闭合阻断口腔通道，舌身平放，气流完全从鼻腔振动涌出，声带振动。",
        typical_error: "若双唇漏气",
        correction_tip:
            "嘴唇闭得不紧导致气流从嘴里漏了。上下唇必须闭紧，让声音完全从鼻子里面哼出来。",
        visual_weight: 0.35,
    },
    ArticulatoryGuidanceEntry {
        ipa: "n",
        category: "浊齿龈鼻音",
        lips: "嘴唇自然张开",
        teeth: "上下齿微开",
        tongue: "舌尖紧紧贴死上牙龈阻断口腔通道",
        airflow: "气流经由鼻腔冲出",
        vocal_cords: "声带持续振动",
        summary: "舌尖紧紧贴死上牙龈阻断口腔通道，嘴唇自然张开，气流经由鼻腔冲出，声带振动。",
        typical_error: "若舌尖脱位发成元音",
        correction_tip: "舌尖没有封严上牙龈。舌尖必须贴死牙龈，强制把气流逼入鼻腔振动。",
        visual_weight: 0.15,
    },
    ArticulatoryGuidanceEntry {
        ipa: "ŋ",
        category: "浊软腭鼻音",
        lips: "双唇微张",
        teeth: "牙齿微开",
        tongue: "舌根用力抬起并贴死软腭，完全封死咽喉通往口腔的道路，舌尖不可碰牙龈",
        airflow: "声音在后鼻腔剧烈共鸣",
        vocal_cords: "声带持续振动",
        summary: "舌根用力抬起并贴死软腭封死口腔，双唇微张，声音在后鼻腔剧烈共鸣，声带振动。",
        typical_error: "若发成前鼻音 /n/",
        correction_tip: "舌头放得太靠前了！舌尖绝对不要碰牙龈，完全靠舌根向后上方抬起贴死软腭。",
        visual_weight: 0.05,
    },
    ArticulatoryGuidanceEntry {
        ipa: "l",
        category: "浊舌侧边音",
        lips: "双唇放松微张",
        teeth: "上下齿微开",
        tongue: "舌尖坚决抵住上齿龈中点不动，舌身两侧留空",
        airflow: "让气流与声音顺着舌头两侧边缘滑出",
        vocal_cords: "声带持续振动",
        summary: "舌尖坚决抵住上齿龈中点不动，双唇放松微张，让气流与声音顺着舌头两侧边缘滑出。",
        typical_error: "若发成中流音 /w/ 或 /r/",
        correction_tip: "舌尖脱位了！舌尖必须紧紧顶死上牙槽，声音顺着舌头两边流出来。",
        visual_weight: 0.15,
    },
    ArticulatoryGuidanceEntry {
        ipa: "r",
        category: "浊卷舌近音",
        lips: "双唇向前微圆",
        teeth: "牙齿自然微启",
        tongue: "舌尖向后上方高高卷起并完全悬空（不可触碰口腔任何地方）",
        airflow: "气流从悬空卷起的舌尖下部顺畅流出，F3 共振峰深度骤降",
        vocal_cords: "声带饱满振动",
        summary: "舌尖向后上方高高卷起并完全悬空（不可碰触任何地方），双唇向前微圆，喉音饱满。",
        typical_error: "若舌尖碰到了牙龈",
        correction_tip: "舌头千万不要碰牙龈（碰了就成了 /l/）！舌尖必须悬空卷起，唇部微噘。",
        visual_weight: 0.20,
    },
    ArticulatoryGuidanceEntry {
        ipa: "w",
        category: "浊圆唇近音",
        lips: "双唇收缩成极小的紧圆孔向前突出，随后迅速向周围松开滑向后续音",
        teeth: "牙齿微张",
        tongue: "舌后部向软腭高抬，随后迅速滑开",
        airflow: "圆唇通道内平滑流出",
        vocal_cords: "声带持续振动",
        summary: "双唇收缩成极小的紧圆孔向前突出，舌后部向软腭高抬，随后双唇迅速向周围松开滑行。",
        typical_error: "若嘴唇不圆",
        correction_tip: "嘴唇没有撅起！双唇必须先收缩成小吸管状，像要吹蜡烛一样，瞬间滑开。",
        visual_weight: 0.35,
    },
    ArticulatoryGuidanceEntry {
        ipa: "j",
        category: "浊硬腭近音",
        lips: "嘴唇扁平展开呈微笑状",
        teeth: "牙齿微张",
        tongue: "舌前部向硬腭中央高高拱起（接近 /iː/ 的舌位但更紧凑），随后迅速滑开",
        airflow: "气流擦过硬腭",
        vocal_cords: "声带持续振动",
        summary: "舌前部向硬腭中央高高拱起，气流擦过硬腭，随后迅速滑向后续元音。",
        typical_error: "若滑行过缓发成元音",
        correction_tip: "滑动太拖沓。舌面前部迅速拱起贴近天花板，瞬间借力滑向下一个元音。",
        visual_weight: 0.20,
    },
];

/// Looks up Chinese embodied articulatory guidance for a given phoneme symbol.
pub fn get_phoneme_articulatory_guidance(
    phoneme_symbol: &str,
) -> Option<&'static ArticulatoryGuidanceEntry> {
    let norm = crate::phoneme::inventory::normalize_phoneme_symbol(phoneme_symbol);
    PHONEME_GUIDANCE_CATALOG
        .iter()
        .find(|entry| entry.ipa == norm || entry.ipa == phoneme_symbol)
}

/// Retrieves dynamic visual weight w_visual for a given phoneme (RFC Section 6.3).
pub fn get_dynamic_visual_weight(phoneme_symbol: &str) -> f32 {
    get_phoneme_articulatory_guidance(phoneme_symbol)
        .map(|entry| entry.visual_weight)
        .unwrap_or(0.15)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_all_44_phonemes_have_guidance() {
        assert_eq!(PHONEME_GUIDANCE_CATALOG.len(), 44);
        for entry in PHONEME_GUIDANCE_CATALOG {
            assert!(!entry.lips.is_empty());
            assert!(!entry.teeth.is_empty());
            assert!(!entry.tongue.is_empty());
            assert!(!entry.airflow.is_empty());
            assert!(!entry.vocal_cords.is_empty());
            assert!(!entry.summary.is_empty());
            assert!(!entry.typical_error.is_empty());
            assert!(!entry.correction_tip.is_empty());
            assert!(entry.visual_weight >= 0.0 && entry.visual_weight <= 0.40);
        }
    }

    #[test]
    fn test_lookup_aliases_and_normalization() {
        let entry_caret = get_phoneme_articulatory_guidance("ʌ");
        assert!(entry_caret.is_some());
        assert_eq!(entry_caret.unwrap().ipa, "ʌ");

        let entry_caret_alias = get_phoneme_articulatory_guidance("V");
        assert!(entry_caret_alias.is_some());
        assert_eq!(entry_caret_alias.unwrap().ipa, "ʌ");

        let entry_ee = get_phoneme_articulatory_guidance("i:");
        assert!(entry_ee.is_some());
        assert_eq!(entry_ee.unwrap().ipa, "iː");
    }

    #[test]
    fn test_dynamic_visual_weights() {
        // Bilabials and labiodentals have high visual weight 0.35
        assert_eq!(get_dynamic_visual_weight("p"), 0.35);
        assert_eq!(get_dynamic_visual_weight("f"), 0.35);
        assert_eq!(get_dynamic_visual_weight("w"), 0.35);
        // Velars and glottals have low visual weight 0.05
        assert_eq!(get_dynamic_visual_weight("k"), 0.05);
        assert_eq!(get_dynamic_visual_weight("h"), 0.05);
    }
}
