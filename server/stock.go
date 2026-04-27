package main

import (
	"encoding/json"
	"math"
	"math/rand"
	"sort"
	"sync"
	"time"
)

const (
	MsgTypeSnapshot  = "snapshot"
	MsgTypeStockTick = "stock_tick"
	MsgTypeIndexTick = "index_tick"
)

// ── Domain structs ────────────────────────────────────────────────────────────

type StockItem struct {
	Symbol        string  `json:"symbol"`
	Name          string  `json:"name"`
	Exchange      string  `json:"exchange"`
	Price         float64 `json:"price"`
	Reference     float64 `json:"reference"` // Giá tham chiếu (đóng cửa hôm qua)
	Change        float64 `json:"change"`
	ChangePercent float64 `json:"changePercent"`
	Open          float64 `json:"open"`
	High          float64 `json:"high"`
	Low           float64 `json:"low"`
	Ceiling       float64 `json:"ceiling"` // Giá trần
	Floor         float64 `json:"floor"`   // Giá sàn
	Volume        int64   `json:"volume"`
	TotalValue    float64 `json:"totalValue"` // Giá trị giao dịch (VND)
}

type MarketIndex struct {
	Name          string  `json:"name"`
	Value         float64 `json:"value"`
	Change        float64 `json:"change"`
	ChangePercent float64 `json:"changePercent"`
	Volume        int64   `json:"volume"`
	Advances      int     `json:"advances"`  // Số mã tăng
	Declines      int     `json:"declines"`  // Số mã giảm
	NoChanges     int     `json:"noChanges"` // Số mã đứng
}

type MarketMessage struct {
	Type    string `json:"type"`
	Payload any    `json:"payload"`
}

type SnapshotPayload struct {
	Stocks  []*StockItem   `json:"stocks"`
	Indices []*MarketIndex `json:"indices"`
	Status  string         `json:"status"` // "OPEN" | "CLOSED" | "ATC"
}

// ── Seed data ─────────────────────────────────────────────────────────────────

type seedStock struct {
	Symbol   string
	Name     string
	Ref      float64 // VND
	Exchange string
}

var seeds = []seedStock{
	{"VCB", "Vietcombank", 85400, "HOSE"},
	{"BID", "BIDV", 44200, "HOSE"},
	{"CTG", "VietinBank", 33800, "HOSE"},
	{"TCB", "Techcombank", 25600, "HOSE"},
	{"MBB", "MBBank", 20100, "HOSE"},
	{"VNM", "Vinamilk", 68200, "HOSE"},
	{"FPT", "FPT Corp", 123000, "HOSE"},
	{"HPG", "Hòa Phát Group", 26800, "HOSE"},
	{"MSN", "Masan Group", 67500, "HOSE"},
	{"MWG", "Mobile World", 45600, "HOSE"},
	{"GAS", "PV Gas", 82000, "HOSE"},
	{"SSI", "SSI Securities", 29400, "HOSE"},
	{"VHM", "Vinhomes", 32100, "HOSE"},
	{"VIC", "Vingroup", 40200, "HOSE"},
	{"PLX", "Petrolimex", 38500, "HOSE"},
	{"REE", "REE Corp", 52300, "HOSE"},
	{"DGC", "Đức Giang Chemicals", 68900, "HOSE"},
	{"PNJ", "PNJ Gold", 85000, "HOSE"},
	{"ACB", "ACB Bank", 23400, "HNX"},
	{"SHB", "SHB", 9800, "HNX"},
}

var indexSeeds = []struct {
	Name  string
	Value float64
}{
	{"VNINDEX", 1248.56},
	{"HNX-Index", 228.43},
	{"UPCOM-Index", 91.27},
}

// ── Simulator ─────────────────────────────────────────────────────────────────

type StockSimulator struct {
	stocks     map[string]*StockItem
	stockOrder []string // giữ thứ tự stable cho snapshot
	indices    []*MarketIndex
	indexRef   []float64
	mu         sync.RWMutex
	rng        *rand.Rand
}

func newStockSimulator() *StockSimulator {
	rng := rand.New(rand.NewSource(time.Now().UnixNano()))
	sim := &StockSimulator{
		stocks: make(map[string]*StockItem),
		rng:    rng,
	}

	for _, s := range seeds {
		// HOSE: ±7%, HNX/UPCOM: ±10%
		limitFactor := 0.07
		if s.Exchange != "HOSE" {
			limitFactor = 0.10
		}

		// Mở cửa đã lệch khỏi tham chiếu ±2%
		openOffset := (rng.Float64() - 0.5) * 0.04
		open := roundPrice(s.Ref * (1 + openOffset))

		// Giá hiện tại đã chạy thêm ±1.5% từ giá mở
		currentOffset := (rng.Float64() - 0.5) * 0.03
		price := roundPrice(open * (1 + currentOffset))

		ceiling := roundPrice(s.Ref * (1 + limitFactor))
		floor := roundPrice(s.Ref * (1 - limitFactor))
		price = math.Max(floor, math.Min(ceiling, price))

		vol := int64(rng.Int63n(5_000_000) + 500_000)
		stock := &StockItem{
			Symbol:    s.Symbol,
			Name:      s.Name,
			Exchange:  s.Exchange,
			Reference: s.Ref,
			Open:      open,
			Price:     price,
			High:      math.Max(open, price),
			Low:       math.Min(open, price),
			Ceiling:   ceiling,
			Floor:     floor,
			Volume:    vol,
			TotalValue: float64(vol) * price,
		}
		stock.Change = round2(stock.Price - stock.Reference)
		stock.ChangePercent = round2(stock.Change / stock.Reference * 100)

		sim.stocks[s.Symbol] = stock
		sim.stockOrder = append(sim.stockOrder, s.Symbol)
	}

	// Sort order theo change% giảm dần (như real UI)
	sort.Slice(sim.stockOrder, func(i, j int) bool {
		return sim.stocks[sim.stockOrder[i]].ChangePercent > sim.stocks[sim.stockOrder[j]].ChangePercent
	})

	for _, s := range indexSeeds {
		// Index cũng đã chạy ±1% từ đầu phiên
		offset := (rng.Float64() - 0.5) * 0.02
		val := round2(s.Value * (1 + offset))
		sim.indices = append(sim.indices, &MarketIndex{
			Name:  s.Name,
			Value: val,
		})
		sim.indexRef = append(sim.indexRef, s.Value)
	}

	sim.recalcIndices()
	return sim
}

// tick cập nhật 3–6 cổ phiếu ngẫu nhiên và trả về bản sao của chúng.
func (s *StockSimulator) tick() []*StockItem {
	s.mu.Lock()
	defer s.mu.Unlock()

	count := 3 + s.rng.Intn(4)
	perm := s.rng.Perm(len(s.stockOrder))

	updated := make([]*StockItem, 0, count)
	for i := 0; i < count && i < len(perm); i++ {
		sym := s.stockOrder[perm[i]]
		stock := s.stocks[sym]

		// Biến động ±0.2–0.8% mỗi tick + lực mean-reversion nhẹ
		volatility := 0.002 + s.rng.Float64()*0.006
		direction := s.rng.Float64() - 0.5
		reversion := (stock.Reference - stock.Price) * 0.004

		newPrice := roundPrice(stock.Price + stock.Price*volatility*direction*2 + reversion)
		newPrice = math.Max(stock.Floor, math.Min(stock.Ceiling, newPrice))

		if newPrice == stock.Price {
			continue
		}

		stock.Price = newPrice
		stock.Change = round2(stock.Price - stock.Reference)
		stock.ChangePercent = round2(stock.Change / stock.Reference * 100)

		if newPrice > stock.High {
			stock.High = newPrice
		}
		if newPrice < stock.Low {
			stock.Low = newPrice
		}

		vol := int64(s.rng.Int63n(50_000) + 2_000)
		stock.Volume += vol
		stock.TotalValue += float64(vol) * newPrice

		cp := *stock
		updated = append(updated, &cp)
	}
	return updated
}

// tickIndices cập nhật tất cả index và trả về bản sao.
func (s *StockSimulator) tickIndices() []*MarketIndex {
	s.mu.Lock()
	defer s.mu.Unlock()

	s.recalcIndices()

	result := make([]*MarketIndex, len(s.indices))
	for i, idx := range s.indices {
		cp := *idx
		result[i] = &cp
	}
	return result
}

// recalcIndices tính lại giá trị index từ tập cổ phiếu hiện tại.
// Gọi khi đang giữ mutex.
func (s *StockSimulator) recalcIndices() {
	advances, declines, noChange := 0, 0, 0
	totalVol := int64(0)
	for _, st := range s.stocks {
		switch {
		case st.Change > 0:
			advances++
		case st.Change < 0:
			declines++
		default:
			noChange++
		}
		totalVol += st.Volume
	}

	for i, idx := range s.indices {
		ref := s.indexRef[i]
		// Biến động nhỏ hơn stock ±0.1–0.4%
		v := 0.001 + s.rng.Float64()*0.003
		d := s.rng.Float64() - 0.5
		reversion := (ref - idx.Value) * 0.003
		newVal := round2(idx.Value + idx.Value*v*d*2 + reversion)
		newVal = math.Max(ref*0.93, math.Min(ref*1.07, newVal))

		idx.Value = newVal
		idx.Change = round2(newVal - ref)
		idx.ChangePercent = round2(idx.Change / ref * 100)
		idx.Volume = totalVol
		idx.Advances = advances
		idx.Declines = declines
		idx.NoChanges = noChange
	}
}

func (s *StockSimulator) snapshot() *SnapshotPayload {
	s.mu.RLock()
	defer s.mu.RUnlock()

	stocks := make([]*StockItem, 0, len(s.stockOrder))
	for _, sym := range s.stockOrder {
		cp := *s.stocks[sym]
		stocks = append(stocks, &cp)
	}

	indices := make([]*MarketIndex, len(s.indices))
	for i, idx := range s.indices {
		cp := *idx
		indices[i] = &cp
	}

	return &SnapshotPayload{Stocks: stocks, Indices: indices, Status: "OPEN"}
}

func (s *StockSimulator) buildMsg(msgType string, payload any) []byte {
	data, _ := json.Marshal(MarketMessage{Type: msgType, Payload: payload})
	return data
}

// ── Helpers ───────────────────────────────────────────────────────────────────

// roundPrice làm tròn về bội số 100 VND (đơn vị tick nhỏ nhất trên HOSE).
func roundPrice(p float64) float64 {
	return math.Round(p/100) * 100
}

func round2(v float64) float64 {
	return math.Round(v*100) / 100
}
