import { i as e, n as t, t as n } from "./jsx-runtime-C1t0izHF.mjs";
// node_modules/framer-motion/dist/es/context/LayoutGroupContext.mjs
var r = /* @__PURE__ */ e(t(), 1), i = /* @__PURE__ */ (0, r.createContext)({});
// node_modules/framer-motion/dist/es/utils/use-constant.mjs
function a(e) {
	let t = (0, r.useRef)(null);
	return t.current === null && (t.current = e()), t.current;
}
// node_modules/framer-motion/dist/es/utils/use-isomorphic-effect.mjs
var o = typeof window < "u" ? r.useLayoutEffect : r.useEffect, s = /* @__PURE__ */ (0, r.createContext)(null);
// node_modules/motion-utils/dist/es/array.mjs
function c(e, t) {
	e.indexOf(t) === -1 && e.push(t);
}
function l(e, t) {
	let n = e.indexOf(t);
	n > -1 && e.splice(n, 1);
}
// node_modules/motion-utils/dist/es/clamp.mjs
var u = (e, t, n) => n > t ? t : n < e ? e : n, d = {}, f = (e) => /^-?(?:\d+(?:\.\d+)?|\.\d+)$/u.test(e), p = (e) => typeof e == "object" && !!e, m = (e) => /^0[^.\s]+$/u.test(e);
// node_modules/motion-utils/dist/es/memo.mjs
/*#__NO_SIDE_EFFECTS__*/
function h(e) {
	let t;
	return () => (t === void 0 && (t = e()), t);
}
// node_modules/motion-utils/dist/es/noop.mjs
var g = /* @__NO_SIDE_EFFECTS__ */ (e) => e, _ = (...e) => e.reduce((e, t) => (n) => t(e(n))), v = /* @__NO_SIDE_EFFECTS__ */ (e, t, n) => {
	let r = t - e;
	return r ? (n - e) / r : 1;
}, y = class {
	constructor() {
		this.subscriptions = [];
	}
	add(e) {
		return c(this.subscriptions, e), () => this.remove(e);
	}
	remove(e) {
		l(this.subscriptions, e);
	}
	notify(e, t, n) {
		let r = this.subscriptions.length;
		if (r) {
			if (r === 1) this.subscriptions[0](e, t, n);
			else for (let i = 0; i < r; i++) {
				let r = this.subscriptions[i];
				r && r(e, t, n);
			}
		}
	}
	getSize() {
		return this.subscriptions.length;
	}
	clear() {
		this.subscriptions.length = 0;
	}
}, b = /* @__NO_SIDE_EFFECTS__ */ (e) => e * 1e3, x = /* @__NO_SIDE_EFFECTS__ */ (e) => e / 1e3, S = /* @__NO_SIDE_EFFECTS__ */ (e, t) => t ? 1e3 / t * e : 0, C = (e, t, n) => e * (3 * (1 - e) * ((1 - e) * t + e * n) + e * e);
/*#__NO_SIDE_EFFECTS__*/
function w(e, t, n, r) {
	return e === t && n === r ? g : (i) => {
		let a = 0, o = 1, s = i;
		for (let t = 0;; t++) {
			s >= a && s <= o || (s = (a + o) / 2);
			let r = C(s, e, n) - i;
			if (r * r < 1e-28 || t === 40) break;
			r > 0 ? o = s : a = s, s -= r * 1e-7 / (C(s + 1e-7, e, n) - i - r);
		}
		return C(s, t, r);
	};
}
// node_modules/motion-utils/dist/es/easing/modifiers/mirror.mjs
var T = /* @__NO_SIDE_EFFECTS__ */ (e) => (t) => t <= .5 ? e(2 * t) / 2 : (2 - e(2 * (1 - t))) / 2, ee = /* @__NO_SIDE_EFFECTS__ */ (e) => (t) => 1 - e(1 - t), te = /*@__PURE__*/ w(.33, 1.53, .69, .99), ne = /*@__PURE__*/ ee(te), E = /*@__PURE__*/ T(ne), re = (e) => e >= 1 ? 1 : (e *= 2) < 1 ? .5 * ne(e) : .5 * (2 - 2 ** (-10 * (e - 1))), ie = (e) => 1 - Math.sin(Math.acos(e)), ae = /* @__PURE__ */ ee(ie), oe = /* @__PURE__ */ T(ie), se = /*@__PURE__*/ w(.42, 0, 1, 1), ce = /*@__PURE__*/ w(0, 0, .58, 1), le = /*@__PURE__*/ w(.42, 0, .58, 1), ue = /* @__NO_SIDE_EFFECTS__ */ (e) => Array.isArray(e) && typeof e[0] != "number", de = /* @__NO_SIDE_EFFECTS__ */ (e) => Array.isArray(e) && typeof e[0] == "number", fe = {
	linear: g,
	easeIn: se,
	easeInOut: le,
	easeOut: ce,
	circIn: ie,
	circInOut: oe,
	circOut: ae,
	backIn: ne,
	backInOut: E,
	backOut: te,
	anticipate: re
}, pe = (e) => typeof e == "string", me = (e) => {
	if (/* @__PURE__ */ de(e)) {
		e.length;
		let [t, n, r, i] = e;
		return /* @__PURE__ */ w(t, n, r, i);
	}
	return pe(e) ? (fe[e], `${e}`, fe[e]) : e;
}, D = {
	delta: 0,
	timestamp: 0,
	isProcessing: !1
}, he;
function ge() {
	he = void 0;
}
var O = {
	now: () => (he === void 0 && O.set(D.isProcessing || d.useManualTiming ? D.timestamp : performance.now()), he),
	set: (e) => {
		he = e, queueMicrotask(ge);
	}
}, _e = (e) => Math.round(e * 1e5) / 1e5, ve = (e) => (t) => typeof t == "string" && t.startsWith(e), ye = /*@__PURE__*/ ve("--"), be = /*@__PURE__*/ ve("var(--"), xe = (e) => be(e) ? Se.test(e.split("/*")[0].trim()) : !1, Se = /var\(--(?:[\w-]+\s*|[\w-]+\s*,(?:\s*[^)(\s]|\s*\((?:[^)(]|\([^)(]*\))*\))+\s*)\)$/iu;
function Ce(e) {
	return typeof e == "string" && e.split("/*")[0].includes("var(--");
}
// node_modules/motion-dom/dist/es/value/types/numbers/index.mjs
var we = {
	test: (e) => typeof e == "number",
	parse: parseFloat,
	transform: (e) => e
}, Te = {
	...we,
	transform: (e) => u(0, 1, e)
}, Ee = {
	...we,
	default: 1
}, De = /-?(?:\d+(?:\.\d+)?|\.\d+)/gu;
// node_modules/motion-dom/dist/es/value/types/utils/is-nullish.mjs
function Oe(e) {
	return e == null;
}
// node_modules/motion-dom/dist/es/value/types/utils/single-color-regex.mjs
var ke = /^(?:#[\da-f]{3,8}|(?:rgb|hsl)a?\((?:-?[\d.]+%?[,\s]+){2}-?[\d.]+%?\s*(?:[,/]\s*)?(?:\b\d+(?:\.\d+)?|\.\d+)?%?\))$/iu, Ae = (e, t) => (n) => !!(typeof n == "string" && ke.test(n) && n.startsWith(e) || t && !Oe(n) && Object.prototype.hasOwnProperty.call(n, t)), je = (e, t, n) => (r) => {
	if (typeof r != "string") return r;
	let [i, a, o, s] = r.match(De);
	return {
		[e]: parseFloat(i),
		[t]: parseFloat(a),
		[n]: parseFloat(o),
		alpha: s === void 0 ? 1 : parseFloat(s)
	};
}, Me = (e) => u(0, 255, e), Ne = {
	...we,
	transform: (e) => Math.round(Me(e))
}, Pe = {
	test: /*@__PURE__*/ Ae("rgb", "red"),
	parse: /*@__PURE__*/ je("red", "green", "blue"),
	transform: ({ red: e, green: t, blue: n, alpha: r = 1 }) => "rgba(" + Ne.transform(e) + ", " + Ne.transform(t) + ", " + Ne.transform(n) + ", " + _e(Te.transform(r)) + ")"
};
// node_modules/motion-dom/dist/es/value/types/color/hex.mjs
function Fe(e) {
	let t = "", n = "", r = "", i = "";
	return e.length > 5 ? (t = e.substring(1, 3), n = e.substring(3, 5), r = e.substring(5, 7), i = e.substring(7, 9)) : (t = e.substring(1, 2), n = e.substring(2, 3), r = e.substring(3, 4), i = e.substring(4, 5), t += t, n += n, r += r, i += i), {
		red: parseInt(t, 16),
		green: parseInt(n, 16),
		blue: parseInt(r, 16),
		alpha: i ? parseInt(i, 16) / 255 : 1
	};
}
var Ie = {
	test: /*@__PURE__*/ Ae("#"),
	parse: Fe,
	transform: Pe.transform
}, Le = /* @__NO_SIDE_EFFECTS__ */ (e) => ({
	test: (t) => typeof t == "string" && t.endsWith(e) && t.split(" ").length === 1,
	parse: parseFloat,
	transform: (t) => `${t}${e}`
}), k = /*@__PURE__*/ Le("deg"), A = /*@__PURE__*/ Le("%"), j = /*@__PURE__*/ Le("px"), Re = /*@__PURE__*/ Le("vh"), ze = /*@__PURE__*/ Le("vw"), Be = {
	...A,
	parse: (e) => A.parse(e) / 100,
	transform: (e) => A.transform(e * 100)
}, Ve = {
	test: /*@__PURE__*/ Ae("hsl", "hue"),
	parse: /*@__PURE__*/ je("hue", "saturation", "lightness"),
	transform: ({ hue: e, saturation: t, lightness: n, alpha: r = 1 }) => "hsla(" + Math.round(e) + ", " + A.transform(_e(t)) + ", " + A.transform(_e(n)) + ", " + _e(Te.transform(r)) + ")"
}, M = {
	test: (e) => Pe.test(e) || Ie.test(e) || Ve.test(e),
	parse: (e) => Pe.test(e) ? Pe.parse(e) : Ve.test(e) ? Ve.parse(e) : Ie.parse(e),
	transform: (e) => typeof e == "string" ? e : e.hasOwnProperty("red") ? Pe.transform(e) : Ve.transform(e),
	getAnimatableNone: (e) => {
		let t = M.parse(e);
		return t.alpha = 0, M.transform(t);
	}
}, He = /(?:#[\da-f]{3,8}|(?:rgb|hsl)a?\((?:-?[\d.]+%?[,\s]+){2}-?[\d.]+%?\s*(?:[,/]\s*)?(?:\b\d+(?:\.\d+)?|\.\d+)?%?\))/giu, Ue = /*@__PURE__*/ new RegExp(De.source), We = /*@__PURE__*/ new RegExp(He.source, "i");
function Ge(e) {
	return isNaN(e) && typeof e == "string" && (Ue.test(e) || We.test(e));
}
var Ke = "number", qe = "color", Je = "var", Ye = "var(", Xe = "${}", Ze = /var\s*\(\s*--(?:[\w-]+\s*|[\w-]+\s*,(?:\s*[^)(\s]|\s*\((?:[^)(]|\([^)(]*\))*\))+\s*)\)|#[\da-f]{3,8}|(?:rgb|hsl)a?\((?:-?[\d.]+%?[,\s]+){2}-?[\d.]+%?\s*(?:[,/]\s*)?(?:\b\d+(?:\.\d+)?|\.\d+)?%?\)|-?(?:\d+(?:\.\d+)?|\.\d+)(?![a-z]*\()/giu;
function Qe(e) {
	let t = e.toString();
	return Ue.test(t) || We.test(t);
}
function $e(e) {
	let t = e.toString(), n = [], r = {
		color: [],
		number: [],
		var: []
	}, i = [], a = 0;
	return {
		values: n,
		split: t.replace(Ze, (e) => (M.test(e) ? (r.color.push(a), i.push(qe), n.push(M.parse(e))) : e.startsWith(Ye) ? (r.var.push(a), i.push(Je), n.push(e)) : (r.number.push(a), i.push(Ke), n.push(parseFloat(e))), ++a, Xe)).split(Xe),
		indexes: r,
		types: i
	};
}
function et(e) {
	return $e(e).values;
}
function tt({ split: e, types: t }) {
	let n = e.length;
	return (r) => {
		let i = "";
		for (let a = 0; a < n; a++) if (i += e[a], r[a] !== void 0) {
			let e = t[a];
			i += e === Ke ? _e(r[a]) : e === qe ? M.transform(r[a]) : r[a];
		}
		return i;
	};
}
function nt(e) {
	return tt($e(e));
}
var rt = (e) => typeof e == "number" ? 0 : M.test(e) ? M.getAnimatableNone(e) : e, it = (e, t) => typeof e == "number" ? t?.trim().endsWith("/") ? e : 0 : rt(e);
function at(e) {
	let t = $e(e);
	return tt(t)(t.values.map((e, n) => it(e, t.split[n])));
}
var N = {
	test: Ge,
	parse: et,
	createTransformer: nt,
	getAnimatableNone: at
};
// node_modules/motion-dom/dist/es/value/types/color/hsla-to-rgba.mjs
function ot(e, t, n) {
	return n < 0 && (n += 1), n > 1 && --n, n < 1 / 6 ? e + (t - e) * 6 * n : n < 1 / 2 ? t : n < 2 / 3 ? e + (t - e) * (2 / 3 - n) * 6 : e;
}
function st({ hue: e, saturation: t, lightness: n, alpha: r }) {
	e /= 360, t /= 100, n /= 100;
	let i = 0, a = 0, o = 0;
	if (!t) i = a = o = n;
	else {
		let r = n < .5 ? n * (1 + t) : n + t - n * t, s = 2 * n - r;
		i = ot(s, r, e + 1 / 3), a = ot(s, r, e), o = ot(s, r, e - 1 / 3);
	}
	return {
		red: Math.round(i * 255),
		green: Math.round(a * 255),
		blue: Math.round(o * 255),
		alpha: r
	};
}
// node_modules/motion-dom/dist/es/utils/mix/immediate.mjs
function ct(e, t) {
	return (n) => n > 0 ? t : e;
}
// node_modules/motion-dom/dist/es/utils/mix/number.mjs
var P = (e, t, n) => e + (t - e) * n, lt = (e, t, n) => {
	let r = e * e, i = n * (t * t - r) + r;
	return i < 0 ? 0 : Math.sqrt(i);
}, ut = [
	Ie,
	Pe,
	Ve
], dt = (e) => ut.find((t) => t.test(e));
function ft(e) {
	let t = dt(e);
	if (!t) return `${e}`, !1;
	let n = t.parse(e);
	return t === Ve && (n = st(n)), n;
}
var pt = (e, t) => {
	let n = ft(e), r = ft(t);
	if (!n || !r) return ct(e, t);
	let i = { ...n };
	return (e) => (i.red = lt(n.red, r.red, e), i.green = lt(n.green, r.green, e), i.blue = lt(n.blue, r.blue, e), i.alpha = P(n.alpha, r.alpha, e), Pe.transform(i));
}, mt = /* @__PURE__ */ new Set(["none", "hidden"]);
function ht(e, t) {
	return mt.has(e) ? (n) => n <= 0 ? e : t : (n) => n >= 1 ? t : e;
}
// node_modules/motion-dom/dist/es/utils/mix/complex.mjs
function gt(e, t) {
	return (n) => P(e, t, n);
}
function _t(e) {
	return typeof e == "number" ? gt : typeof e == "string" ? xe(e) ? ct : M.test(e) ? pt : xt : Array.isArray(e) ? vt : typeof e == "object" ? M.test(e) ? pt : yt : ct;
}
function vt(e, t) {
	let n = [...e], r = n.length, i = e.map((e, n) => _t(e)(e, t[n]));
	return (e) => {
		for (let t = 0; t < r; t++) n[t] = i[t](e);
		return n;
	};
}
function yt(e, t) {
	let n = {
		...e,
		...t
	}, r = {};
	for (let i in n) e[i] !== void 0 && t[i] !== void 0 && (r[i] = _t(e[i])(e[i], t[i]));
	return (e) => {
		for (let t in r) n[t] = r[t](e);
		return n;
	};
}
function bt(e, t) {
	let n = [], r = {
		color: 0,
		var: 0,
		number: 0
	};
	for (let i = 0; i < t.values.length; i++) {
		let a = t.types[i], o = e.indexes[a][r[a]], s = e.values[o] ?? 0;
		n[i] = s, r[a]++;
	}
	return n;
}
var xt = (e, t) => {
	let n = N.createTransformer(t), r = $e(e), i = $e(t);
	return r.indexes.var.length === i.indexes.var.length && r.indexes.color.length === i.indexes.color.length && r.indexes.number.length >= i.indexes.number.length ? mt.has(e) && !i.values.length || mt.has(t) && !r.values.length ? ht(e, t) : _(vt(bt(r, i), i.values), n) : (`${e}${t}`, ct(e, t));
}, St = /^(-?(?:\d+(?:\.\d*)?|\.\d+))([a-z%]*)$/iu;
function Ct(e, t) {
	let n = St.exec(e);
	if (!n) return;
	let r = St.exec(t);
	if (!r || n[2] !== r[2]) return;
	let i = n[2], a = parseFloat(n[1]), o = parseFloat(r[1]);
	return (e) => _e(P(a, o, e)) + i;
}
function wt(e, t, n) {
	if (typeof e == "number" && typeof t == "number" && typeof n == "number") return P(e, t, n);
	if (typeof e == "string" && typeof t == "string") {
		let n = Ct(e, t);
		if (n) return n;
	}
	return _t(e)(e, t);
}
// node_modules/motion-dom/dist/es/frameloop/order.mjs
var Tt = [
	"setup",
	"read",
	"resolveKeyframes",
	"preUpdate",
	"update",
	"preRender",
	"render",
	"postRender"
];
// node_modules/motion-dom/dist/es/frameloop/render-step.mjs
function Et(e) {
	let t = /* @__PURE__ */ new Set(), n = /* @__PURE__ */ new Set(), r = !1, i = !1, a, o = /* @__PURE__ */ new Set(), s = {
		delta: 0,
		timestamp: 0,
		isProcessing: !1
	};
	function c(t) {
		o.has(t) && (n.add(t), e()), (a = t)(s);
	}
	let l = {
		schedule: (e, i = !1, s = !1) => {
			let c = s && r, l = c ? t : n;
			return i && o.add(e), c && e !== a && l.delete(e), l.add(e), e;
		},
		cancel: (e) => {
			n.delete(e), o.delete(e);
		},
		process: (e) => {
			if (s = e, r) {
				i = !0;
				return;
			}
			r = !0;
			let o = t;
			t = n, n = o, t.forEach(c), t.clear(), a = void 0, r = !1, i && (i = !1, l.process(e));
		}
	};
	return l;
}
// node_modules/motion-dom/dist/es/frameloop/batcher.mjs
var Dt = 40;
function Ot(e, t, n = {
	delta: 0,
	timestamp: 0,
	isProcessing: !1
}) {
	let r = !1, i = !0, a = () => r = !0, o = Tt.reduce((e, t) => (e[t] = Et(a), e), {}), { setup: s, read: c, resolveKeyframes: l, preUpdate: u, update: f, preRender: p, render: m, postRender: h } = o, g = () => {
		let a = d.useManualTiming, o = a ? n.timestamp : performance.now();
		r = !1, a || (n.delta = i ? 1e3 / 60 : Math.max(Math.min(o - n.timestamp, Dt), 1)), n.timestamp = o, n.isProcessing = !0, s.process(n), c.process(n), l.process(n), u.process(n), f.process(n), p.process(n), m.process(n), h.process(n), n.isProcessing = !1, r && t && (i = !1, e(g));
	}, _ = () => {
		r = !0, i = !0, n.isProcessing || e(g);
	};
	return {
		schedule: Tt.reduce((e, t) => {
			let n = o[t];
			return e[t] = (e, t = !1, i = !1) => (r || _(), n.schedule(e, t, i)), e;
		}, {}),
		cancel: (e) => {
			for (let t = 0; t < Tt.length; t++) o[Tt[t]].cancel(e);
		},
		state: n,
		steps: o
	};
}
// node_modules/motion-dom/dist/es/frameloop/frame.mjs
var { schedule: F, cancel: I, steps: kt } = /* @__PURE__ */ Ot(typeof requestAnimationFrame < "u" ? requestAnimationFrame : g, !0, D), At = (e) => {
	let t = ({ timestamp: t }) => e(t);
	return {
		start: (e = !0) => F.update(t, e),
		stop: () => I(t),
		now: () => D.isProcessing ? D.timestamp : O.now()
	};
}, jt = (e, t, n = 10) => {
	let r = "", i = Math.max(Math.round(t / n), 2);
	for (let t = 0; t < i; t++) r += Math.round(e(t / (i - 1)) * 1e4) / 1e4 + ", ";
	return `linear(${r.substring(0, r.length - 2)})`;
}, Mt = 2e4;
function Nt(e, t = 50, n = Mt, r) {
	let i = 0, a = e.next(i);
	for (r?.push(a.value); !a.done && i < n;) i += t, a = e.next(i), r?.push(a.value);
	return i >= n ? Infinity : i;
}
// node_modules/motion-dom/dist/es/animation/generators/utils/create-generator-easing.mjs
function Pt(e, t = 100, n) {
	let { keyframes: r, velocity: i } = e, a = r && r[r.length - 1] - r[0], o = n({
		...e,
		velocity: i && a ? i * t / a : i,
		keyframes: [0, t]
	}), s = Math.min(Nt(o), Mt);
	return {
		type: "keyframes",
		ease: (e) => o.next(s * e).value / t,
		duration: /* @__PURE__ */ x(s)
	};
}
// node_modules/motion-dom/dist/es/animation/generators/spring.mjs
var L = {
	stiffness: 100,
	damping: 10,
	mass: 1,
	duration: 800,
	bounce: .3,
	visualDuration: .3,
	restSpeed: {
		granular: .01,
		default: 2
	},
	restDelta: {
		granular: .005,
		default: .5
	},
	minDuration: .01,
	maxDuration: 10,
	minDamping: .05
}, Ft = (e) => e < 0 ? 1 / Math.max(1 + e, L.minDamping) : Math.max(1 - e, L.minDamping);
function It(e, t) {
	if (!(e > 1)) return 1;
	let n = Math.sqrt(e * e - 1), r = e - n, i = e + n, a = 2 * n * Math.exp(-t) * (1 + t);
	return zt((e) => i * Math.exp(-r * e) - r * Math.exp(-i * e) - a, (e) => Math.exp(-i * e) - Math.exp(-r * e), t) / t;
}
function Lt(e, t) {
	return e * Math.sqrt(1 - t * t);
}
var Rt = 12;
function zt(e, t, n) {
	let r = n;
	for (let n = 1; n < Rt; n++) r -= e(r) / t(r);
	return r;
}
var Bt = .001;
function Vt({ duration: e = L.duration, bounce: t = L.bounce }) {
	let n, r;
	L.maxDuration;
	let i = Ft(t);
	e = u(L.minDuration, L.maxDuration, /* @__PURE__ */ x(e)), i < 1 ? (n = (t) => {
		let n = t * i, r = n * e, a = Lt(t, i), o = Math.exp(-r);
		return Bt - n / a * o;
	}, r = (t) => {
		let r = t * i * e, a = i * i * t * t * e, o = Math.exp(-r), s = Lt(t * t, i);
		return (-n(t) + Bt > 0 ? -1 : 1) * -a * o / s;
	}) : (n = (t) => -.001 + Math.exp(-t * e) * (t * e + 1), r = (t) => Math.exp(-t * e) * (-t * (e * e)));
	let a = 5 / e, o = zt(n, r, a), s = o * It(i, o * e), c = s * s;
	return {
		stiffness: c,
		damping: i * 2 * Math.sqrt(c),
		duration: /* @__PURE__ */ b(e)
	};
}
var Ht = (e, t) => (t ? e >= 0 : e > 0) && e < Infinity;
function Ut(e, t) {
	if (Ht(e, t)) return e;
}
function Wt(e) {
	let t = Ut(e.stiffness), n = Ut(e.damping, !0), r = Ut(e.mass), i = {
		...e,
		stiffness: t ?? L.stiffness,
		damping: n ?? L.damping,
		mass: r ?? L.mass,
		isResolvedFromDuration: !1,
		isTimeDefined: (t ?? n ?? r) === void 0 && (e.duration !== void 0 || e.bounce !== void 0)
	};
	if (i.isTimeDefined) {
		if (e.visualDuration) {
			let t = Ft(e.bounce || 0), n = 2 * Math.PI / (e.visualDuration * 1.2) * It(t, 2 * Math.PI / 1.2);
			i.stiffness = n * n, i.damping = 2 * t * Math.sqrt(i.stiffness);
		} else Object.assign(i, Vt(i)), i.isResolvedFromDuration = !0;
		Ht(i.stiffness) && Ht(i.damping, !0) || (i.stiffness = L.stiffness, i.damping = L.damping);
	}
	return i;
}
function Gt(e = L.visualDuration, t = L.bounce) {
	let n = typeof e == "object" ? e : {
		visualDuration: e,
		keyframes: [0, 1],
		bounce: t
	}, r = n.keyframes[0], i = n.keyframes[n.keyframes.length - 1], a = {
		done: !1,
		value: r
	}, { stiffness: o, damping: s, mass: c, duration: l, isResolvedFromDuration: u, isTimeDefined: d } = Wt({ ...n }), f = (e) => d ? 0 : -/* @__PURE__ */ x(e), p = s / (2 * Math.sqrt(o * c)), m = /* @__PURE__ */ x(Math.sqrt(o / c)), h = p * m, g = {
		target: i,
		delta: i - r,
		velocity: f(n.velocity || 0) || 0,
		restSpeed: 0,
		restDelta: 0
	}, _ = () => {
		let e = Math.abs(g.delta) < 5;
		g.restSpeed = n.restSpeed || (e ? L.restSpeed.granular : L.restSpeed.default), g.restDelta = n.restDelta || (e ? L.restDelta.granular : L.restDelta.default);
	};
	_();
	let v, y, S;
	if (p < 1) {
		let e = Lt(m, p), t = {
			A: 0,
			sinC: 0,
			cosC: 0,
			t: -1,
			env: 0,
			sin: 0,
			cos: 0
		};
		S = () => {
			t.A = (g.velocity + h * g.delta) / e, t.sinC = h * t.A + g.delta * e, t.cosC = h * g.delta - t.A * e;
		};
		let n = (n) => {
			n !== t.t && (t.t = n, t.env = Math.exp(-h * n), t.sin = Math.sin(e * n), t.cos = Math.cos(e * n));
		};
		v = (e) => (n(e), g.target - t.env * (t.A * t.sin + g.delta * t.cos)), y = (e) => (n(e), t.env * (t.sinC * t.sin + t.cosC * t.cos));
	} else if (p === 1) {
		v = (e) => g.target - Math.exp(-m * e) * (g.delta + (g.velocity + m * g.delta) * e);
		let e = { C: 0 };
		S = () => {
			e.C = g.velocity + m * g.delta;
		}, y = (t) => Math.exp(-m * t) * (m * e.C * t - g.velocity);
	} else {
		let e = m * Math.sqrt(p * p - 1), t = h - e, n = h + e, r = d ? Infinity : 300 / e, i = (e, t) => Math.exp(t > r ? -e * r - h * (t - r) : -e * t), a = {
			S: 0,
			F: 0
		};
		S = () => {
			let t = (g.velocity + h * g.delta) / e;
			a.S = (g.delta + t) / 2, a.F = (g.delta - t) / 2;
		}, v = (e) => g.target - a.S * i(t, e) - a.F * i(n, e), y = (e) => t * a.S * i(t, e) + n * a.F * i(n, e);
	}
	S();
	let C = u && l || null, w = {
		calculatedDuration: C,
		retarget: (e, t) => {
			g.target = e[e.length - 1], g.delta = g.target - e[0], g.velocity = f(t), n.restSpeed && n.restDelta || _(), w.calculatedDuration = C, a.done = !1, S();
		},
		velocity: (e) => /* @__PURE__ */ b(y(e)),
		next: (e) => {
			let t = v(e);
			if (u) a.done = e >= l;
			else {
				let n = /* @__PURE__ */ b(y(e));
				a.done = Math.abs(n) <= g.restSpeed && Math.abs(g.target - t) <= g.restDelta;
			}
			return a.value = a.done ? g.target : t, a;
		},
		toString: () => {
			let e = Math.min(Nt(w), Mt), t = jt((t) => w.next(e * t).value, e, 30);
			return e + "ms " + t;
		},
		toTransition: () => {}
	};
	return w;
}
var Kt = /* @__PURE__ */ Object.assign(Gt, { applyToOptions: (e) => {
	let t = Pt(e, 100, Gt);
	return e.ease = t.ease, e.duration = /* @__PURE__ */ b(t.duration), e.type = "keyframes", e;
} });
// node_modules/motion-dom/dist/es/animation/generators/inertia.mjs
function qt({ keyframes: e, velocity: t = 0, power: n = .8, timeConstant: r = 325, bounceDamping: i = 10, bounceStiffness: a = 500, modifyTarget: o, min: s, max: c, restDelta: l = .5, restSpeed: u }) {
	let d = e[0], f = {
		done: !1,
		value: d
	}, p = (e) => e < s || e > c, m = (e) => s === void 0 ? c : c === void 0 || Math.abs(s - e) < Math.abs(c - e) ? s : c, h = n * t, g = d + h, _ = o === void 0 ? g : o(g);
	_ !== g && (h = _ - d);
	let v = (e) => -h * Math.exp(-e / r), y = (e) => {
		let t = v(e);
		f.done = Math.abs(t) <= l, f.value = f.done ? _ : _ + t;
	}, b, x, S = (e) => {
		p(f.value) && (b = e, x = Kt({
			keyframes: [f.value, m(f.value)],
			velocity: -v(e) / r * 1e3,
			damping: i,
			stiffness: a,
			restDelta: l,
			restSpeed: u
		}));
	};
	return S(0), {
		calculatedDuration: null,
		next: (e) => {
			let t = !1;
			return !x && b === void 0 && (t = !0, y(e), S(e)), b !== void 0 && e >= b ? x.next(e - b) : (!t && y(e), f);
		}
	};
}
// node_modules/motion-dom/dist/es/utils/interpolate.mjs
function Jt(e, t, n) {
	let r = [], i = n || d.mix || wt, a = e.length - 1;
	for (let n = 0; n < a; n++) {
		let a = i(e[n], e[n + 1]);
		t && (a = _(Array.isArray(t) ? t[n] || g : t, a)), r.push(a);
	}
	return r;
}
function Yt(e, t, { clamp: n = !0, ease: r, mixer: i } = {}) {
	let a = e.length;
	if (t.length, a === 1) return () => t[0];
	if (a === 2 && t[0] === t[1]) return () => t[1];
	let o = e[0] === e[1];
	e[0] > e[a - 1] && (e = [...e].reverse(), t = [...t].reverse());
	let s = Jt(t, r, i), c = s.length, l = (n) => {
		if (o && n < e[0]) return t[0];
		let r = 0;
		if (c > 1) for (; r < e.length - 2 && !(n < e[r + 1]); r++);
		let i = /* @__PURE__ */ v(e[r], e[r + 1], n);
		return s[r](i);
	};
	return n ? (t) => l(u(e[0], e[a - 1], t)) : l;
}
// node_modules/motion-dom/dist/es/animation/keyframes/offsets/fill.mjs
function Xt(e, t) {
	let n = e[e.length - 1];
	for (let r = 1; r <= t; r++) {
		let i = /* @__PURE__ */ v(0, t, r);
		e.push(P(n, 1, i));
	}
}
// node_modules/motion-dom/dist/es/animation/keyframes/offsets/default.mjs
function Zt(e) {
	let t = [0];
	return Xt(t, e.length - 1), t;
}
// node_modules/motion-dom/dist/es/animation/keyframes/offsets/time.mjs
function Qt(e, t) {
	return e.map((e) => e * t);
}
// node_modules/motion-dom/dist/es/animation/generators/keyframes.mjs
function $t(e, t) {
	return e.map(() => t || le).splice(0, e.length - 1);
}
function R({ duration: e = 300, keyframes: t, times: n, ease: r = "easeInOut" }) {
	let i = /* @__PURE__ */ ue(r) ? r.map(me) : me(r) || le, a = {
		done: !1,
		value: t[0]
	};
	if (t.length === 2 && !Array.isArray(i) && (!n || n.length !== 2 || n[0] === 0 && n[1] === 1)) {
		let [n, r] = t, o = n === r ? void 0 : (d.mix || wt)(n, r);
		return {
			calculatedDuration: e,
			next: (t) => (a.value = o ? o(i(e > 0 ? u(0, 1, t / e) : 1)) : r, a.done = t >= e, a)
		};
	}
	let o = Yt(Qt(n && n.length === t.length ? n : Zt(t), e), t, { ease: Array.isArray(i) ? i : $t(t, i) });
	return {
		calculatedDuration: e,
		next: (t) => (a.value = o(t), a.done = t >= e, a)
	};
}
// node_modules/motion-dom/dist/es/animation/generators/utils/velocity.mjs
var en = 5;
function tn(e, t, n) {
	let r = Math.max(t - en, 0);
	return /* @__PURE__ */ S(n - e(r), t - r);
}
function nn(e, t, n = 0) {
	return t <= 0 ? n : e.velocity ? e.velocity(t) : tn((t) => e.next(t).value, t, e.next(t).value);
}
// node_modules/motion-dom/dist/es/animation/keyframes/get-final.mjs
var rn = (e) => e !== null;
function an(e, { repeat: t, repeatType: n = "loop" }, r, i = 1) {
	let a = e.filter(rn), o = i < 0 || t && n !== "loop" && t % 2 == 1 ? 0 : a.length - 1;
	return !o || r === void 0 ? a[o] : r;
}
// node_modules/motion-dom/dist/es/animation/utils/replace-transition-type.mjs
var on = {
	decay: qt,
	inertia: qt,
	tween: R,
	keyframes: R,
	spring: Kt
};
function sn(e) {
	typeof e.type == "string" && (e.type = on[e.type]);
}
// node_modules/motion-dom/dist/es/animation/utils/resolve-start-time.mjs
var cn = 40, ln = (e, t) => t - e > cn && !d.useManualTiming ? t : e;
// node_modules/motion-dom/dist/es/animation/utils/notify-inspector.mjs
function un(e, t) {
	return {
		kind: e,
		animation: t,
		timestamp: O.now(),
		frameTimestamp: D.timestamp,
		frameIsProcessing: D.isProcessing
	};
}
function dn(e, t, n) {
	let r = globalThis.__MOTION_INSPECT__;
	if (r) try {
		r({
			...un("animation-start", e),
			options: n ? {
				...t,
				...n
			} : t
		});
	} catch {}
}
function fn(e, t) {
	let n = globalThis.__MOTION_INSPECT__;
	if (n) try {
		n({
			...un("layout-animation-start", e),
			node: t
		});
	} catch {}
}
// node_modules/motion-dom/dist/es/animation/utils/WithPromise.mjs
var pn = class {
	constructor() {
		this.isResolved = !1;
	}
	get finished() {
		return this._finished ||= this.isResolved ? Promise.resolve() : new Promise((e) => {
			this._resolve = e;
		}), this._finished;
	}
	updateFinished() {
		this._finished = this._resolve = void 0, this.isResolved = !1;
	}
	notifyFinished() {
		this.isResolved = !0, this._resolve?.();
	}
	then(e, t) {
		return this.finished.then(e, t);
	}
}, mn = (e) => e / 100, hn = (e) => Math.round(e * 1e4) / 1e4, gn = class extends pn {
	constructor(e) {
		super(), this.state = "idle", this.startTime = null, this.isStopped = !1, this.currentTime = 0, this.holdTime = null, this.playbackSpeed = 1, this.delayState = {
			done: !1,
			value: void 0
		}, this.stop = () => {
			let { motionValue: e } = this.options;
			e && e.updatedAt !== O.now() && this.tick(O.now()), this.isStopped = !0, this.state !== "idle" && (this.teardown(), this.options.onStop?.());
		}, this.options = e, this.initAnimation(), this.play(), e.autoplay === !1 && this.pause(), dn(this, this.options);
	}
	initAnimation() {
		let { options: e } = this;
		sn(e);
		let { type: t = R, repeat: n = 0, repeatDelay: r = 0, repeatType: i, velocity: a = 0 } = e, { keyframes: o } = e, s = t || R;
		s !== R && typeof o[0] != "number" && (this.mixKeyframes = _(mn, wt(o[0], o[1])), o = [0, 100]);
		let c = s(o === e.keyframes ? e : {
			...e,
			keyframes: o
		});
		i === "mirror" && (this.mirroredGenerator = s({
			...e,
			keyframes: [...o].reverse(),
			velocity: -a
		})), c.calculatedDuration === null && (c.calculatedDuration = Nt(c));
		let { calculatedDuration: l } = c;
		this.calculatedDuration = l, this.resolvedDuration = l + r, this.totalDuration = this.resolvedDuration * (n + 1) - r, this.generator = c;
	}
	updateTime(e) {
		let t = Math.round(e - this.startTime) * this.playbackSpeed;
		this.currentTime = this.holdTime === null ? t : this.holdTime;
	}
	tick(e, t = !1) {
		let { generator: n, totalDuration: r, calculatedDuration: i } = this;
		if (this.startTime === null) return n.next(0);
		let { delay: a = 0, keyframes: o, type: s, onUpdate: c, finalKeyframe: l } = this.options;
		this.startTime === this.pendingStartTime && (this.startTime = ln(this.startTime, e)), this.pendingStartTime = void 0, this.speed > 0 ? this.startTime = Math.min(this.startTime, e) : this.speed < 0 && (this.startTime = Math.min(e - r / this.speed, this.startTime)), t ? this.currentTime = e : this.updateTime(e);
		let u = this.currentTime - a * (this.playbackSpeed >= 0 ? 1 : -1), d = this.playbackSpeed >= 0 ? u < 0 : u > r;
		this.currentTime = Math.max(u, 0), this.state === "finished" && this.holdTime === null && (this.currentTime = r);
		let f = this.generate(this.currentTime, d), { done: p } = f;
		!d && i !== null && (p = this.playbackSpeed >= 0 ? this.currentTime >= r : this.currentTime <= 0);
		let m = this.holdTime === null && (this.state === "finished" || this.state === "running" && p);
		return m && s !== qt && (f.value = an(o, this.options, l, this.speed)), c && c(f.value), m && this.finish(), f;
	}
	at(e) {
		return this.holdTime ?? hn((e - this.startTime) * this.playbackSpeed);
	}
	sampleAt(e, t) {
		let { options: n, totalDuration: r } = this, i = this.at(e) - (n.delay || 0);
		return i >= r && n.type !== qt ? an(n.keyframes, n, t ? void 0 : n.finalKeyframe) : this.generate(Math.max(i, 0), i < 0).value;
	}
	generate(e, t) {
		let { generator: n, totalDuration: r, mixKeyframes: i, mirroredGenerator: a, resolvedDuration: o } = this, { keyframes: s, repeat: c, repeatType: l, repeatDelay: d } = this.options, f = e, p = n;
		if (c) {
			let t = Math.min(e, r) / o, n = Math.floor(t), i = t % 1;
			!i && t >= 1 && (i = 1), i === 1 && n--, n = Math.min(n, c + 1), n % 2 && (l === "reverse" ? (i = 1 - i, d && (i -= d / o)) : l === "mirror" && (p = a)), f = u(0, 1, i) * o;
		}
		let m;
		return t ? (this.delayState.value = s[0], m = this.delayState) : m = p.next(f), i && !t && (m.value = i(m.value)), m;
	}
	then(e, t) {
		return this.finished.then(e, t);
	}
	get duration() {
		return /* @__PURE__ */ x(this.calculatedDuration);
	}
	get iterationDuration() {
		let { delay: e = 0 } = this.options || {};
		return this.duration + /* @__PURE__ */ x(e);
	}
	get time() {
		return /* @__PURE__ */ x(this.currentTime);
	}
	set time(e) {
		e = /* @__PURE__ */ b(e), this.currentTime = e, this.startTime === null || this.holdTime !== null || this.playbackSpeed === 0 ? this.holdTime = e : this.driver && (this.startTime = this.driver.now() - e / this.playbackSpeed), this.driver ? this.driver.start(!1) : (this.startTime = 0, this.state = "paused", this.holdTime = e, this.tick(e));
	}
	getGeneratorVelocity() {
		return nn(this.generator, this.currentTime, this.options.velocity);
	}
	get speed() {
		return this.playbackSpeed;
	}
	set speed(e) {
		let t = this.playbackSpeed !== e;
		t && this.driver && this.updateTime(O.now()), this.playbackSpeed = e, t && this.driver && (this.time = /* @__PURE__ */ x(this.currentTime));
	}
	play() {
		if (this.isStopped) return;
		let { driver: e = At, startTime: t } = this.options;
		this.driver ||= e((e) => this.tick(e)), this.options.onPlay?.();
		let n = this.driver.now();
		this.state === "finished" ? (this.updateFinished(), this.startTime = n) : this.holdTime === null ? this.startTime ||= t ?? (this.pendingStartTime = n) : this.startTime = n - this.holdTime / (this.playbackSpeed || 1), this.state === "finished" && this.speed < 0 && (this.startTime += this.calculatedDuration), this.holdTime = null, this.state = "running", this.driver.start();
	}
	pause() {
		this.state = "paused", this.updateTime(O.now()), this.holdTime = this.currentTime;
	}
	complete() {
		this.state !== "running" && this.play(), this.state = "finished", this.holdTime = null;
	}
	finish() {
		this.notifyFinished(), this.teardown(), this.state = "finished", this.options.onComplete?.();
	}
	cancel() {
		this.holdTime = null, this.startTime = 0, this.tick(0), this.teardown(), this.options.onCancel?.();
	}
	teardown() {
		this.state = "idle", this.stopDriver(), this.startTime = this.holdTime = null;
	}
	stopDriver() {
		this.driver &&= (this.driver.stop(), void 0);
	}
	sample(e) {
		return this.startTime = 0, this.tick(e, !0);
	}
	attachTimeline(e) {
		return this.options.allowFlatten && (this.options.type = "keyframes", this.options.ease = "linear", this.initAnimation()), this.driver?.stop(), e.observe(this);
	}
}, _n = /* @__PURE__ */ new Set([
	"brightness",
	"contrast",
	"saturate",
	"opacity"
]);
function vn(e) {
	let [t, n] = e.slice(0, -1).split("(");
	if (t === "drop-shadow") return e;
	let [r] = n.match(De) || [];
	if (!r) return e;
	let i = n.replace(r, ""), a = +!!_n.has(t);
	return r !== n && (a *= 100), t + "(" + a + i + ")";
}
var yn = /\b([a-z-]*)\(.*?\)/gu, bn = {
	...N,
	getAnimatableNone: (e) => {
		let t = e.match(yn);
		return t ? t.map(vn).join(" ") : e;
	}
}, xn = {
	...N,
	getAnimatableNone: (e) => {
		let t = N.parse(e);
		return N.createTransformer(e)(t.map((e) => typeof e == "number" ? 0 : typeof e == "object" ? {
			...e,
			alpha: 1
		} : e));
	}
}, Sn = {
	...we,
	transform: Math.round
}, Cn = {
	borderWidth: j,
	borderTopWidth: j,
	borderRightWidth: j,
	borderBottomWidth: j,
	borderLeftWidth: j,
	borderRadius: j,
	borderTopLeftRadius: j,
	borderTopRightRadius: j,
	borderBottomRightRadius: j,
	borderBottomLeftRadius: j,
	width: j,
	maxWidth: j,
	height: j,
	maxHeight: j,
	top: j,
	right: j,
	bottom: j,
	left: j,
	inset: j,
	insetBlock: j,
	insetBlockStart: j,
	insetBlockEnd: j,
	insetInline: j,
	insetInlineStart: j,
	insetInlineEnd: j,
	padding: j,
	paddingTop: j,
	paddingRight: j,
	paddingBottom: j,
	paddingLeft: j,
	paddingBlock: j,
	paddingBlockStart: j,
	paddingBlockEnd: j,
	paddingInline: j,
	paddingInlineStart: j,
	paddingInlineEnd: j,
	margin: j,
	marginTop: j,
	marginRight: j,
	marginBottom: j,
	marginLeft: j,
	marginBlock: j,
	marginBlockStart: j,
	marginBlockEnd: j,
	marginInline: j,
	marginInlineStart: j,
	marginInlineEnd: j,
	fontSize: j,
	backgroundPositionX: j,
	backgroundPositionY: j,
	rotate: k,
	pathRotation: k,
	rotateX: k,
	rotateY: k,
	rotateZ: k,
	scale: Ee,
	scaleX: Ee,
	scaleY: Ee,
	scaleZ: Ee,
	skew: k,
	skewX: k,
	skewY: k,
	distance: j,
	translateX: j,
	translateY: j,
	translateZ: j,
	x: j,
	y: j,
	z: j,
	perspective: j,
	transformPerspective: j,
	opacity: Te,
	originX: Be,
	originY: Be,
	originZ: j,
	zIndex: Sn,
	fillOpacity: Te,
	strokeOpacity: Te,
	numOctaves: Sn
}, wn = {
	...Cn,
	color: M,
	backgroundColor: M,
	outlineColor: M,
	fill: M,
	stroke: M,
	borderColor: M,
	borderTopColor: M,
	borderRightColor: M,
	borderBottomColor: M,
	borderLeftColor: M,
	filter: bn,
	WebkitFilter: bn,
	mask: xn,
	WebkitMask: xn
}, Tn = (e) => wn[e], En = /*@__PURE__*/ new Set([bn, xn]);
function Dn(e, t) {
	let n = Tn(e);
	return En.has(n) || (n = N), n.getAnimatableNone ? n.getAnimatableNone(t) : void 0;
}
// node_modules/motion-dom/dist/es/animation/keyframes/utils/fill-wildcards.mjs
function On(e) {
	for (let t = 1; t < e.length; t++) e[t] ?? (e[t] = e[t - 1]);
}
// node_modules/motion-dom/dist/es/render/dom/parse-transform.mjs
var kn = (e) => e * 180 / Math.PI, An = (e) => Mn(kn(Math.atan2(e[1], e[0]))), jn = {
	x: 4,
	y: 5,
	translateX: 4,
	translateY: 5,
	scaleX: 0,
	scaleY: 3,
	scaleZ: () => 1,
	scale: (e) => (Math.abs(e[0]) + Math.abs(e[3])) / 2,
	rotate: An,
	rotateZ: An,
	skewX: (e) => kn(Math.atan(e[1])),
	skewY: (e) => kn(Math.atan(e[2])),
	skew: (e) => (Math.abs(e[1]) + Math.abs(e[2])) / 2
}, Mn = (e) => (e %= 360, e < 0 && (e += 360), e), Nn = An, Pn = (e) => Math.hypot(e[0], e[1]), Fn = (e) => Math.hypot(e[4], e[5]), In = {
	x: 12,
	y: 13,
	z: 14,
	translateX: 12,
	translateY: 13,
	translateZ: 14,
	scaleX: Pn,
	scaleY: Fn,
	scaleZ: (e) => Math.hypot(e[8], e[9], e[10]),
	scale: (e) => (Pn(e) + Fn(e)) / 2,
	rotateX: (e) => Mn(kn(Math.atan2(e[6], e[5]))),
	rotateY: (e) => Mn(kn(Math.atan2(-e[2], e[0]))),
	rotateZ: Nn,
	rotate: Nn,
	skewX: (e) => kn(Math.atan(e[4])),
	skewY: (e) => kn(Math.atan(e[1])),
	skew: (e) => (Math.abs(e[1]) + Math.abs(e[4])) / 2
};
function Ln(e) {
	return +!!e.includes("scale");
}
function Rn(e, t) {
	if (!e || e === "none") return Ln(t);
	let n = e.match(/^matrix3d\(([-\d.e\s,]+)\)$/u), r, i;
	if (n) r = In, i = n;
	else {
		let t = e.match(/^matrix\(([-\d.e\s,]+)\)$/u);
		r = jn, i = t;
	}
	if (!i) return Ln(t);
	let a = r[t], o = i[1].split(",").map(Bn);
	return typeof a == "function" ? a(o) : o[a];
}
var zn = (e, t) => {
	let { transform: n = "none" } = getComputedStyle(e);
	return Rn(n, t);
};
function Bn(e) {
	return parseFloat(e.trim());
}
// node_modules/motion-dom/dist/es/render/utils/keys-transform.mjs
var z = [
	"transformPerspective",
	"x",
	"y",
	"z",
	"translateX",
	"translateY",
	"translateZ",
	"scale",
	"scaleX",
	"scaleY",
	"scaleZ",
	"rotate",
	"rotateX",
	"rotateY",
	"rotateZ",
	"skew",
	"skewX",
	"skewY"
], B = /* @__PURE__ */ new Set([...z, "pathRotation"]), Vn = (e) => e === we || e === j, Hn = /* @__PURE__ */ new Set([
	"x",
	"y",
	"z"
]), Un = /* @__PURE__ */ z.filter((e) => !Hn.has(e));
function Wn(e) {
	let t = [];
	return Un.forEach((n) => {
		let r = e.getValue(n);
		if (r !== void 0) {
			let e = r.get(), i = +!!n.startsWith("scale");
			if (e === i) return;
			t.push([n, e]), r.set(i);
		}
	}), t;
}
var Gn = /* @__PURE__ */ new Set(["bottom", "right"]);
function Kn(e, t, n, r, i, a) {
	let o = parseFloat(e);
	if (!isNaN(o)) return o;
	let { min: s, max: c } = t()[n], l = c - s;
	return a === "border-box" ? l : l - parseFloat(r) - parseFloat(i);
}
var qn = ({ transform: e }) => Rn(e, "x"), Jn = ({ transform: e }) => Rn(e, "y"), Yn = {
	width: ({ width: e, paddingLeft: t = "0", paddingRight: n = "0", boxSizing: r }, i) => Kn(e, i, "x", t, n, r),
	height: ({ height: e, paddingTop: t = "0", paddingBottom: n = "0", boxSizing: r }, i) => Kn(e, i, "y", t, n, r),
	top: ({ top: e }) => parseFloat(e),
	left: ({ left: e }) => parseFloat(e),
	bottom: ({ top: e }, t) => {
		let { y: n } = t();
		return parseFloat(e) + (n.max - n.min);
	},
	right: ({ left: e }, t) => {
		let { x: n } = t();
		return parseFloat(e) + (n.max - n.min);
	},
	x: qn,
	y: Jn,
	translateX: qn,
	translateY: Jn
}, Xn = /* @__PURE__ */ new Set(), Zn = !1, Qn = !1, $n = !1;
function er() {
	if (Qn) {
		let e = [], t = /* @__PURE__ */ new Set(), n = /* @__PURE__ */ new Set();
		Xn.forEach((r) => {
			r.needsMeasurement && (e.push(r), t.add(r.element), Gn.has(r.name) && n.add(r.element));
		});
		let r = /* @__PURE__ */ new Map();
		n.forEach((e) => {
			let t = Wn(e);
			t.length && (r.set(e, t), e.render());
		}), e.forEach((e) => e.measureInitialState()), t.forEach((e) => {
			e.render();
			let t = r.get(e);
			t && t.forEach(([t, n]) => {
				e.getValue(t)?.set(n);
			});
		}), e.forEach((e) => e.measureEndState()), e.forEach((e) => {
			e.suspendedScrollY !== void 0 && window.scrollTo(0, e.suspendedScrollY);
		});
	}
	Qn = !1, Zn = !1, Xn.forEach((e) => e.complete($n)), Xn.clear();
}
function tr() {
	Xn.forEach((e) => {
		e.readKeyframes(), e.needsMeasurement && (Qn = !0);
	});
}
function nr() {
	$n = !0, tr(), er(), $n = !1;
}
function rr(e, t, n) {
	if (typeof e == "string") {
		if (f(e) || m(e)) return parseFloat(e);
		if (!N.test(e) && N.test(n)) return Dn(t, n);
	}
	return e ?? void 0;
}
var ir = class {
	constructor(e, t, n, r, i, a = !1) {
		this.state = "pending", this.isAsync = !1, this.needsMeasurement = !1, this.unresolvedKeyframes = [...e], this.onComplete = t, this.name = n, this.motionValue = r, this.element = i, this.isAsync = a;
	}
	scheduleResolve() {
		this.state = "scheduled", this.isAsync ? (Xn.add(this), Zn || (Zn = !0, F.read(tr), F.resolveKeyframes(er))) : (this.readKeyframes(), this.complete());
	}
	readKeyframes() {
		let { unresolvedKeyframes: e, name: t, element: n, motionValue: r } = this;
		if (e[0] === null) {
			let i = r?.get(), a = e[e.length - 1];
			if (i !== void 0) e[0] = i;
			else if (n && t) {
				let r = rr(n.readValue(t, a), t, a);
				r !== void 0 && (e[0] = r);
			}
			e[0] === void 0 && (e[0] = a), r && i === void 0 && r.set(e[0]);
		}
		On(e);
	}
	setFinalKeyframe() {}
	measureInitialState() {}
	renderEndStyles() {}
	measureEndState() {}
	complete(e = !1) {
		this.state = "complete", this.onComplete(this.unresolvedKeyframes, this.finalKeyframe, e), Xn.delete(this);
	}
	cancel() {
		this.state === "scheduled" && (Xn.delete(this), this.state = "pending");
	}
	resume() {
		this.state === "pending" && this.scheduleResolve();
	}
}, ar = (e) => e.startsWith("--");
// node_modules/motion-dom/dist/es/render/dom/style-set.mjs
function or(e, t, n) {
	ar(t) ? e.style.setProperty(t, n) : e.style[t] = n;
}
// node_modules/motion-dom/dist/es/utils/supports/flags.mjs
var sr = {};
// node_modules/motion-dom/dist/es/utils/supports/memo.mjs
function cr(e, t) {
	let n = /* @__PURE__ */ h(e);
	return () => sr[t] ?? n();
}
// node_modules/motion-dom/dist/es/utils/supports/scroll-timeline.mjs
var lr = /* @__PURE__ */ cr(() => window.ScrollTimeline !== void 0, "scrollTimeline"), ur = /*@__PURE__*/ cr(() => {
	try {
		document.createElement("div").animate({ opacity: 0 }, { easing: "linear(0, 1)" });
	} catch {
		return !1;
	}
	return !0;
}, "linearEasing"), dr = ([e, t, n, r]) => `cubic-bezier(${e}, ${t}, ${n}, ${r})`, fr = {
	linear: "linear",
	ease: "ease",
	easeIn: "ease-in",
	easeOut: "ease-out",
	easeInOut: "ease-in-out",
	circIn: /*@__PURE__*/ dr([
		0,
		.65,
		.55,
		1
	]),
	circOut: /*@__PURE__*/ dr([
		.55,
		0,
		1,
		.45
	]),
	backIn: /*@__PURE__*/ dr([
		.31,
		.01,
		.66,
		-.59
	]),
	backOut: /*@__PURE__*/ dr([
		.33,
		1.53,
		.69,
		.99
	])
};
// node_modules/motion-dom/dist/es/animation/waapi/easing/map-easing.mjs
function pr(e, t) {
	if (e) return typeof e == "function" ? ur() ? jt(e, t) : "ease-out" : /* @__PURE__ */ de(e) ? dr(e) : Array.isArray(e) ? e.map((e) => pr(e, t) || fr.easeOut) : fr[e];
}
// node_modules/motion-dom/dist/es/animation/waapi/start-waapi-animation.mjs
function mr(e, t, n, { delay: r = 0, duration: i = 300, repeat: a = 0, repeatType: o = "loop", ease: s = "easeOut", times: c } = {}, l = void 0) {
	let u = { [t]: n };
	c && (u.offset = c);
	let d = pr(s, i);
	Array.isArray(d) && (u.easing = d);
	let f = {
		delay: r,
		duration: i,
		easing: Array.isArray(d) ? "linear" : d,
		fill: "both",
		iterations: a + 1,
		direction: o === "reverse" ? "alternate" : "normal"
	};
	return l && (f.pseudoElement = l), e.animate(u, f);
}
// node_modules/motion-dom/dist/es/animation/generators/utils/is-generator.mjs
function hr(e) {
	return typeof e == "function" && "applyToOptions" in e;
}
// node_modules/motion-dom/dist/es/animation/waapi/utils/apply-generator.mjs
function gr({ type: e, ...t }) {
	return hr(e) && ur() ? e.applyToOptions(t) : (t.duration ??= 300, t.ease ??= "easeOut", t);
}
// node_modules/motion-dom/dist/es/animation/NativeAnimation.mjs
var _r = class extends pn {
	constructor(e) {
		if (super(), this.finishedTime = null, this.isStopped = !1, this.manualStartTime = null, !e) return;
		let { element: t, name: n, keyframes: r, pseudoElement: i, allowFlatten: a = !1, finalKeyframe: o, onComplete: s } = e;
		this.isPseudoElement = !!i, this.allowFlatten = a, this.options = e, e.type;
		let c = gr(e);
		this.animation = mr(t, n, r, c, i), c.autoplay === !1 && this.animation.pause(), this.animation.onfinish = () => {
			if (this.finishedTime = this.time, !i) {
				let e = an(r, this.options, o, this.speed);
				this.updateMotionValue && this.updateMotionValue(e), or(t, n, e), this.animation.cancel();
			}
			s?.(), this.notifyFinished();
		}, dn(this, e, c);
	}
	play() {
		this.isStopped || (this.manualStartTime = null, this.animation.play(), this.state === "finished" && this.updateFinished());
	}
	pause() {
		this.animation.pause();
	}
	complete() {
		this.animation.finish?.();
	}
	cancel() {
		try {
			this.animation.cancel();
		} catch {}
	}
	stop() {
		this.isStopped || (this.isStopped = !0, this.state !== "idle" && this.finishedTime === null && (this.updateMotionValue ? this.updateMotionValue() : this.commitStyles(), this.isPseudoElement || (this.animation.onfinish = null, this.cancel())));
	}
	commitStyles() {
		let e = this.options?.element;
		!this.isPseudoElement && e?.isConnected && this.animation.commitStyles?.();
	}
	get duration() {
		let e = this.animation.effect?.getComputedTiming?.().duration || 0;
		return /* @__PURE__ */ x(Number(e));
	}
	get iterationDuration() {
		let { delay: e = 0 } = this.options || {};
		return this.duration + /* @__PURE__ */ x(e);
	}
	get time() {
		return /* @__PURE__ */ x(Number(this.animation.currentTime) || 0);
	}
	set time(e) {
		let t = this.finishedTime !== null;
		this.manualStartTime = null, this.finishedTime = null, this.animation.currentTime = /* @__PURE__ */ b(e), t && this.animation.pause();
	}
	get speed() {
		return this.animation.playbackRate;
	}
	set speed(e) {
		e < 0 && (this.finishedTime = null), this.animation.playbackRate = e;
	}
	get state() {
		return this.finishedTime === null ? this.animation.playState : "finished";
	}
	get startTime() {
		return this.manualStartTime ?? Number(this.animation.startTime);
	}
	set startTime(e) {
		this.manualStartTime = this.animation.startTime = e;
	}
	attachTimeline({ timeline: e, onAttach: t, observe: n }) {
		return this.allowFlatten && this.animation.effect?.updateTiming({ easing: "linear" }), this.animation.onfinish = null, e && lr() ? (this.animation.timeline = e, t?.(this.animation), g) : n(this);
	}
}, vr = {
	anticipate: re,
	backInOut: E,
	circInOut: oe
};
function yr(e) {
	return e in vr;
}
function br(e) {
	typeof e.ease == "string" && yr(e.ease) && (e.ease = vr[e.ease]);
}
var xr = class extends _r {
	constructor(e) {
		br(e), sn(e), super(e), e.startTime !== void 0 && e.autoplay !== !1 && (this.startTime = e.startTime), this.options = e;
	}
	elapsed() {
		return this.state === "running" ? (O.now() - this.startTime) * this.speed : this.time * 1e3;
	}
	sample(e) {
		if (!this.sampler) {
			let { motionValue: e, onUpdate: t, onComplete: n, onPlay: r, onStop: i, element: a, ...o } = this.options;
			this.sampler = new gn({
				...o,
				autoplay: !1
			}), this.sampler.stop();
		}
		return this.sampler.sample(Math.max(0, e)).value;
	}
	liveValue() {
		let { state: e } = this;
		if (e === "running" || e === "paused") return this.sample(this.elapsed());
	}
	updateMotionValue(e) {
		let { motionValue: t, element: n, name: r } = this.options;
		if (!t) return;
		if (e !== void 0) {
			t.set(e);
			return;
		}
		let { speed: i } = this, a = this.elapsed(), o = this.state === "running" && i ? u(0, 10, a) : 0, s = this.sample(a);
		n && r && or(n, r, s), t.setWithVelocity(this.sample(a - o * Math.sign(i)), s, o / Math.abs(i || 1));
	}
}, Sr = (e, t) => t !== "zIndex" && !!(typeof e == "number" || Array.isArray(e) || typeof e == "string" && (N.test(e) || e === "0") && !e.startsWith("url("));
// node_modules/motion-dom/dist/es/animation/utils/can-animate.mjs
function Cr(e) {
	let t = e[0];
	if (e.length === 1) return !0;
	for (let n = 0; n < e.length; n++) if (e[n] !== t) return !0;
}
function wr(e, t, n, r) {
	let i = e[0];
	if (i === null) return !1;
	if (t === "display" || t === "visibility") return !0;
	let a = e[e.length - 1], o = Sr(i, t), s = Sr(a, t);
	return !o || !s ? (o !== s && `${t}${i}${a}${o ? a : i}`, !1) : Cr(e) || (n === "spring" || hr(n)) && r;
}
// node_modules/motion-dom/dist/es/animation/utils/make-animation-instant.mjs
function Tr(e) {
	e.duration = 0, e.type = "keyframes";
}
// node_modules/motion-dom/dist/es/animation/waapi/utils/accelerated-values.mjs
var Er = /* @__PURE__ */ new Set([
	"opacity",
	"clipPath",
	"filter",
	"transform",
	"backgroundColor"
]), Dr = /^(?:oklch|oklab|lab|lch|color|color-mix|light-dark)\(/;
function Or(e) {
	for (let t = 0; t < e.length; t++) if (typeof e[t] == "string" && Dr.test(e[t])) return !0;
	return !1;
}
// node_modules/motion-dom/dist/es/animation/waapi/supports/waapi.mjs
var kr = /* @__PURE__ */ new Set([
	"color",
	"backgroundColor",
	"outlineColor",
	"fill",
	"stroke",
	"borderColor",
	"borderTopColor",
	"borderRightColor",
	"borderBottomColor",
	"borderLeftColor"
]), Ar = () => !d.useManualTiming && Object.hasOwnProperty.call(Element.prototype, "animate");
function jr(e) {
	let { motionValue: t, name: n, repeatDelay: r, repeatType: i, damping: a, type: o, keyframes: s } = e;
	if (!n || !(Er.has(n) || kr.has(n))) return !1;
	let c = t?.owner?.current;
	if (!(c instanceof HTMLElement) && !(c instanceof SVGElement)) return !1;
	let { onUpdate: l, transformTemplate: u } = t.owner.getProps();
	return Ar() && (Er.has(n) || kr.has(n) && Or(s)) && (n !== "transform" || !u) && !l && !r && i !== "mirror" && a !== 0 && o !== "inertia";
}
// node_modules/motion-dom/dist/es/value/types/utils/get-as-type.mjs
var Mr = (e, t) => t && typeof e == "number" ? t.transform(e) : e, Nr = {
	x: "translateX",
	y: "translateY",
	z: "translateZ",
	transformPerspective: "perspective"
}, Pr = z.length;
function Fr(e, t, n) {
	let r = "", i = !0;
	for (let a = 0; a < Pr; a++) {
		let o = z[a], s = e[o];
		if (s === void 0) continue;
		let c = !0;
		if (typeof s == "number") c = s === +!!o.startsWith("scale");
		else {
			let e = parseFloat(s);
			c = o.startsWith("scale") ? e === 1 : e === 0;
		}
		if (!c || n) {
			let e = Mr(s, Cn[o]);
			if (!c) {
				i = !1;
				let t = Nr[o] || o;
				r += `${t}(${e}) `;
			}
			n && (t[o] = e);
		}
	}
	let a = e.pathRotation;
	return a && (i = !1, r += `rotate(${Mr(a, Cn.pathRotation)}) `), r = r.trim(), n ? r = n(t, i ? "" : r) : i && (r = "none"), r;
}
// node_modules/motion-dom/dist/es/animation/waapi/transforms/groups.mjs
var Ir = /* @__PURE__ */ new WeakMap(), Lr = 200, Rr = .001, zr = .1, Br = .001, Vr = 100, Hr = (e) => /^(linear|ease(In|Out|InOut))$/.test(e), Ur = (e, t) => e - t, Wr = (e, t, n) => {
	let r = [];
	for (let i = 0; i <= n; i++) r.push(P(e, t, i / n));
	return r;
}, Gr = (e) => e.motionValue.owner, Kr = (e) => Ir.get(e), qr = (e, t) => e.latestValues ? e.latestValues[t] : e.getValue(t)?.get(), Jr = ({ name: e, motionValue: t }) => e && z.includes(e) && t?.owner?.current instanceof HTMLElement && Ar() && ur();
function Yr(e) {
	let t = Gr(e), { onUpdate: n, transformTemplate: r } = t.getProps();
	return !e.isHandoff && !n && !e.hasOnUpdate && !r && e.type !== "inertia" && (qr(t, "transform") || "none") === "none" && !qr(t, "pathRotation") && !t.projection?.isProjecting();
}
var Xr = class extends gn {
	constructor(e) {
		let t = Gr(e).current, { driver: n = At } = e, r, i = Yr(e);
		e.driver = (e) => {
			let i = n(e);
			return {
				js: i,
				now: i.now,
				start: (e = !0) => {
					let n = Kr(t);
					!n || n.isJS ? i.start(e) : n.schedule(!1, !e);
				},
				stop: () => {
					i.stop();
					let e = Kr(t);
					e && !e.isFinishing && e.release(r);
				}
			};
		}, super(e), r = this, this.canAccelerate = i;
		let { stop: a } = this;
		this.stop = () => {
			this.isAccelerated() && this.sync(), a();
		};
	}
	get group() {
		return Kr(Gr(this.options).current);
	}
	get js() {
		return this.driver?.js;
	}
	get name() {
		return this.options.name;
	}
	timing() {
		let { startTime: e, speed: t, totalDuration: n } = this, { delay: r = 0, repeatType: i } = this.options;
		return this.holdTime === null ? n === Infinity ? [e + r / t, this.resolvedDuration * (i === "loop" ? 1 : 2) / t || 1] : [e + (r + n) / t, 0] : [Infinity, 0];
	}
	isAccelerated() {
		let { group: e, state: t } = this;
		return e?.tracks.has(this) && !e.isJS && this.canAccelerate && (t === "running" || t === "paused");
	}
	liveValue() {
		return this.isAccelerated() ? this.sampleAt(O.now()) : void 0;
	}
	tween() {
		let { keyframes: e, type: t, ease: n, times: r, repeat: i, delay: a = 0 } = this.options, o = /* @__PURE__ */ de(n) ? dr(n) : Hr(n) && fr[n];
		if (o && !i && !r && e.length === 2 && t === R && this.holdTime === null) {
			let e = this.startTime + a / this.speed;
			return [
				e,
				e + this.resolvedDuration / this.speed,
				o
			];
		}
	}
	boundaries(e, t) {
		let { startTime: n, speed: r, resolvedDuration: i, options: a } = this, { delay: o = 0, repeat: s = 0, repeatDelay: c = 0, repeatType: l, times: u, keyframes: d, type: f } = a, p = (f || R) === R ? u || Zt(d) : [0, 1], m = n + o / r, h = [];
		if (this.holdTime !== null || !(i > 0)) return h;
		for (let n = Math.max(0, Math.floor((e - m) * r / i)); n <= s; n++) {
			let a = m + n * i / r;
			if (a > t) break;
			for (let o of p) {
				let s = a + (n % 2 && l && l !== "loop" ? 1 - o : o) * (i - c) / r;
				s > e && s < t && h.push(s);
			}
		}
		return h;
	}
	numberAt(e) {
		return parseFloat(this.sampleAt(e, !0));
	}
	sync() {
		let e = O.now();
		this.options.motionValue.setWithVelocity(this.sampleAt(e - 10, !0), this.sampleAt(e, !0), 10);
	}
	play() {
		this.isStopped || ((this.group || Zr(Gr(this.options))).tracks.add(this), super.play());
	}
	pause() {
		super.pause(), this.isAccelerated() && (this.sync(), this.group.schedule(!1));
	}
	complete() {
		super.complete();
		let { group: e } = this;
		e && !e.isJS && this.canAccelerate && this.tick(O.now());
	}
	get time() {
		return this.isAccelerated() ? /* @__PURE__ */ x(u(0, this.totalDuration, this.at(O.now()) - (this.options.delay || 0))) : super.time;
	}
	set time(e) {
		super.time = e, this.isAccelerated() && this.sync();
	}
	attachTimeline(e) {
		return this.canAccelerate = !1, this.group?.demote(!0), super.attachTimeline(e);
	}
};
function Zr(e) {
	let t = e.current, n = /* @__PURE__ */ new Set(), r = /* @__PURE__ */ new Map(), i = {}, a = [], o = /* @__PURE__ */ new Map(), s = [], c = !1, l = !1, u = !1, d = !1, f = -Infinity, p = (e) => {
		for (let t of n) if (t.name === e) return !0;
	}, m = (t, n) => {
		let o = {};
		for (let s of t === void 0 ? z : a) {
			let a = r.get(s), c = t === void 0 ? qr(e, s) : a ? a.sampleAt(n > t && !a.timing()[1] ? n : t) : i[s];
			c !== void 0 && (o[s] = c);
		}
		return o;
	}, h = (e, t) => {
		let n = "", r = m(e, t);
		for (let e in r) n += (Nr[e] || e) + "(" + Mr(r[e], Cn[e]) + ") ";
		return n || "none";
	}, g = (e = m()) => {
		t.style.transform = Fr(e, {});
	}, _ = (e = !0) => {
		e && g(), s.forEach(({ animation: e }) => e.cancel()), s = [];
	}, y = (e, t, n = Infinity) => {
		let r = {
			animation: e,
			offset: t,
			end: n
		};
		e.startTime = O.now() + t, n < Infinity && (e.onfinish = () => {
			r.isDone = !0, s.some((e) => e.end === Infinity) && (e.cancel(), s = s.filter((e) => e !== r)), E.schedule(!1);
		}), e.oncancel = () => s.includes(r) && E.demote(), s.push(r);
	}, b = (e, t, n) => {
		let r = (t, n) => Math.abs(e(n) - e(t)), i = r(t, n);
		for (; n - t > .01;) {
			let e = (t + n) / 2;
			r(t, e) > r(e, n) ? n = e : t = e;
		}
		return r(t, n) > i / 2 ? [t, n] : [];
	}, x = (e, t, n, r) => {
		let i = Math.min(Lr, Math.max(2, Math.ceil((n - t) / 10)));
		if (r.length * 4 > i) return;
		let a = Wr(t, n, i).concat(r).sort(Ur), o = (e) => /* @__PURE__ */ v(t, n, e), s, c;
		for (let t of e) {
			let e = a.map((e) => t.numberAt(e)), [n] = e, r = e.map((e) => e - n), i = r[r.length - 1], o = r.some((e) => Math.abs(e) * Rr > Math.abs(i)) ? 0 : i, l = r.map((e) => o ? e / o : e);
			if (!o) {
				if (l.some((e) => !(Math.abs(e) <= Rr))) return;
			} else if (!s) s = l, c = (e) => (t.numberAt(e) - n) / o;
			else if (l.some((e, t) => !(Math.abs(e - s[t]) <= Rr))) return;
		}
		if (!s || s.every((e, t) => Math.abs(e - o(a[t])) <= Rr)) return "linear";
		let l = [], u = (e, t = c(e)) => l.push(hn(t) + " " + hn(o(e) * 100) + "%");
		return s.forEach((e, t) => {
			t && Math.abs(e - s[t - 1]) > .2 && b(c, a[t - 1], a[t]).forEach((e) => u(e)), u(a[t], e);
		}), "linear(" + l + ")";
	}, S = (e, t, n, r) => {
		let i = [t], a = (t, n) => e.every((e) => !(Math.abs(e.numberAt((t + n) / 2) - (e.numberAt(t) + e.numberAt(n)) / 2) > (e.name.startsWith("scale") ? Br : zr))), o = (t, n, r) => {
			if (n - t < 1 || a(t, n)) i.push(n);
			else if (r < 4) o(t, (t + n) / 2, r + 1), o((t + n) / 2, n, r + 1);
			else {
				for (let r of e) i.push(...b((e) => r.numberAt(e), t, n));
				i.push(n);
			}
		}, s = Wr(t, n, Math.ceil((n - t) / 100));
		for (let e of r) s.push(e, Math.min(e + .01, n));
		let c = t;
		for (let e of s.sort(Ur)) e > c && o(c, e, 0), c = e;
		return i.sort(Ur);
	}, C = (e) => {
		let t;
		for (let n of e) {
			let e = n.tween();
			if (!e || t?.some((t, n) => n < 2 ? Math.abs(e[n] - t) > 1 : e[n] !== t)) return;
			t = e;
		}
		return t;
	}, w = (e, n, r, i = { fill: "both" }, a, o) => {
		let s = e.flatMap((e) => e.boundaries(n, r)), c = o || x(e, n, r, s), l = c ? [n, r] : S(e, n, r, s), u = l.map((e) => h(e, a)), d = { transform: u };
		return c || (d.offset = l.map((e) => /* @__PURE__ */ v(n, r, e))), u.every((e) => e === u[0]) && (g(m(n, a)), d = null), t.animate(d, {
			...i,
			duration: r - n,
			easing: c || "linear"
		});
	}, T = (c) => {
		let l = c, u = 0, d = 0, f = [], p = [], m = /* @__PURE__ */ new Set();
		r.clear(), o = /* @__PURE__ */ new Map();
		for (let e of n) {
			r.set(e.name, e);
			let [t, n] = e.timing(), i = t === Infinity;
			if (o.set(e, [
				e.at(c),
				e.speed,
				i
			]), i) e.sync();
			else {
				if (e.speed < 0) return;
				if (n) {
					if (u) {
						let e = (t - d) / n;
						if (Math.abs(n - u) > 1 || Math.abs(e - Math.round(e)) * u > 1) return;
					} else u = n, d = t;
					p.push(e);
				} else m.add(t);
				f.push(e), l = Math.max(l, t);
			}
		}
		i = {}, a = z.filter((t) => {
			if (r.has(t)) return !0;
			let n = i[t] = qr(e, t);
			return n !== void 0 && parseFloat(n) !== +!!t.startsWith("scale");
		}), _(!1);
		let h = !u && C(f);
		try {
			if (h && h[1] > c) {
				let [e, t, n] = h;
				y(w(f, e, t, void 0, void 0, n), e - c, t - e);
			} else l > c && (y(w(f, c, l), 0, l - c), m.forEach((e) => e < l - 1 && y(t.animate(null, { duration: e - c }), 0, e - c)));
			if (u) {
				let e = d + Math.floor((l - d) / u) * u;
				y(w(p, e, e + u, {
					iterations: Infinity,
					iterationStart: (l - e) / u,
					fill: "forwards"
				}, l), l - c);
			}
		} catch {
			return;
		}
		return s.length || _(), !0;
	}, ee = (e) => {
		let t, r = 0;
		for (let i of n) {
			let n = o.get(i);
			if (!n) return !1;
			let [a, s, c] = n, l = i.at(e), u = i.timing()[0] === Infinity;
			if (c) {
				if (u && l === a) continue;
				return !1;
			}
			let d = (l - a) / s, f = u ? 0 : i.speed / s;
			if (t === void 0) t = d, r = f;
			else if (Math.abs(d - t) > 1 || f !== r) return !1;
		}
		if (t === void 0) return !0;
		if (!s.length) return !1;
		for (let { offset: e, end: n, isDone: r } of s) {
			let i = t - e;
			if (n < Infinity && (r ? i < n - 1 : i < 0 || i >= n)) return !1;
		}
		for (let { animation: n, offset: i, isDone: a } of s) {
			if (a) continue;
			let o = t - i;
			r ? (n.playbackRate = r, n.startTime = e - o / r) : (n.pause(), n.currentTime = o);
		}
		return !0;
	}, te = () => !n.size && (u = !0, Kr(t) === E && Ir.delete(t), E.isJS || _(), !0), ne = () => {
		let t = O.now();
		if (E.isJS || (E.isFinishing = !0, n.forEach((n) => {
			let [r, a] = n.timing();
			n.state === "running" && n.speed > 0 && !a && r <= t + 1 && (n.complete(), i[n.name] = qr(e, n.name), o.has(n) || (c = !0));
		}), E.isFinishing = !1), l = !1, u || (n.forEach(({ state: e }, t) => {
			(e === "idle" || e === "finished") && n.delete(t);
		}), te())) return;
		for (let e of n) if (!e.canAccelerate) return E.demote(!0);
		E.isJS && (E.isJS = !1, c = !0, n.forEach((e) => e.js?.stop()));
		let r = c, a = d;
		if (c = d = !1, r || !ee(t)) {
			if (a) {
				if (t - f < Vr) return E.demote();
				f = t;
			}
			if (!T(t)) return E.demote();
		}
	}, E = {
		tracks: n,
		isJS: !1,
		isFinishing: !1,
		schedule(e, t) {
			u || (c ||= e, d ||= !!t, l || (l = !0, D.isProcessing ? F.render(ne, !1, !0) : queueMicrotask(ne)));
		},
		release(e) {
			let { state: t } = e;
			(t === "idle" || t === "finished") && n.delete(e), te() || E.schedule(!0);
		},
		check() {
			if (!E.isJS) {
				for (let t in i) if (qr(e, t) !== i[t] && !p(t)) return E.schedule(!0, !0);
			}
		},
		demote(e) {
			E.isJS || u || (n.forEach((t) => {
				t.isAccelerated() && t.sync(), e || (t.canAccelerate = !1);
			}), E.isJS = !0, o = /* @__PURE__ */ new Map(), _(), n.forEach((e) => e.state === "running" && e.js?.start()));
		}
	};
	return Ir.set(t, E), E;
}
// node_modules/motion-dom/dist/es/animation/AsyncMotionValueAnimation.mjs
var Qr = class extends pn {
	constructor(e) {
		super(), this.stop = () => {
			this._animation && (this._animation.stop(), this.stopTimeline?.()), this.keyframeResolver?.cancel();
		}, this.createdAt = O.now();
		let { keyframes: t, name: n, motionValue: r, element: i } = e, a = e;
		a.autoplay ??= !0, a.delay ??= 0, a.type ??= "keyframes", a.repeat ??= 0, a.repeatDelay ??= 0, a.repeatType ??= "loop";
		let o = i?.KeyframeResolver || ir;
		this.keyframeResolver = new o(t, (e, t, n) => this.onKeyframesResolved(e, t, a, !n), n, r, i), this.keyframeResolver?.scheduleResolve();
	}
	onKeyframesResolved(e, t, n, r) {
		this.keyframeResolver = void 0;
		let { name: i, type: a, velocity: o, delay: s, isHandoff: c, onUpdate: l } = n;
		this.resolvedAt = O.now();
		let u = !0;
		wr(e, i, a, o) || (u = !1, (d.instantAnimations || !s) && l?.(an(e, n, t)), e[0] = e[e.length - 1], Tr(n), n.repeat = 0);
		let { onComplete: f } = n;
		n.finalKeyframe = t, n.keyframes = e, n.onComplete = () => {
			f?.(), this.notifyFinished();
		};
		let p = u && !c && jr(n), m = !p && Jr(n);
		r && (p || m || this.resolvedAt !== this.createdAt) && (n.startTime ??= ln(this.createdAt, this.resolvedAt));
		let h;
		if (p) {
			n.element = n.motionValue?.owner?.current;
			try {
				h = new xr(n);
			} catch {
				h = new gn(n);
			}
		} else h = m ? new Xr(n) : new gn(n);
		this.pendingTimeline &&= (this.stopTimeline = h.attachTimeline(this.pendingTimeline), void 0), this._animation = h;
	}
	get finished() {
		return this._animation ? this._animation.finished : super.finished;
	}
	then(e, t) {
		return this.finished.finally(e).then(() => {});
	}
	liveValue() {
		return this._animation?.liveValue?.();
	}
	get animation() {
		return this._animation || (this.keyframeResolver?.resume(), nr()), this._animation;
	}
	get duration() {
		return this.animation.duration;
	}
	get iterationDuration() {
		return this.animation.iterationDuration;
	}
	get time() {
		return this.animation.time;
	}
	set time(e) {
		this.animation.time = e;
	}
	get speed() {
		return this.animation.speed;
	}
	get state() {
		return this.animation.state;
	}
	set speed(e) {
		this.animation.speed = e;
	}
	get startTime() {
		return this.animation.startTime;
	}
	attachTimeline(e) {
		return this._animation ? this.stopTimeline = this.animation.attachTimeline(e) : this.pendingTimeline = e, () => this.stop();
	}
	play() {
		this.animation.play();
	}
	pause() {
		this.animation.pause();
	}
	complete() {
		this.animation.complete();
	}
	cancel() {
		this._animation && this.animation.cancel(), this.keyframeResolver?.cancel();
	}
};
// node_modules/motion-dom/dist/es/animation/utils/calc-child-stagger.mjs
function $r(e, t, n, r = 0, i = 1) {
	let a = Array.from(e).sort((e, t) => e.sortNodePosition(t)).indexOf(t), o = e.size, s = (o - 1) * r;
	return typeof n == "function" ? n(a, o) : i === 1 ? a * r : s - a * r;
}
// node_modules/motion-dom/dist/es/value/index.mjs
var ei = 30, ti = (e) => !isNaN(parseFloat(e)), ni = { current: void 0 }, ri = class {
	constructor(e, t = {}) {
		this.canTrackVelocity = null, this.events = {}, this.updateAndNotify = (e) => {
			let t = O.now();
			if (this.updatedAt !== t && this.setPrevFrameValue(), this.prev = this.current, this.setCurrent(e), this.current !== this.prev && (this.notifyChange(), this.dependents)) for (let e of this.dependents) e.dirty();
		}, this.hasAnimated = !1, this.setCurrent(e), this.owner = t.owner;
	}
	setCurrent(e) {
		this.current = e, this.updatedAt = O.now(), this.canTrackVelocity === null && e !== void 0 && (this.canTrackVelocity = ti(this.current));
	}
	setPrevFrameValue(e = this.current) {
		this.prevFrameValue = e, this.prevUpdatedAt = this.updatedAt;
	}
	onChange(e) {
		return this.on("change", e);
	}
	on(e, t) {
		var n;
		return e === "change" ? this.onChangeSubscribe(t) : ((n = this.events)[e] || (n[e] = new y())).add(t);
	}
	onChangeSubscribe(e) {
		let { events: t } = this;
		return !t.change && !this.changeSubscriber ? this.changeSubscriber = e : (t.change || (t.change = new y(), t.change.add(this.changeSubscriber), this.changeSubscriber = void 0), t.change.add(e)), () => {
			this.changeSubscriber === e ? this.changeSubscriber = void 0 : t.change?.remove(e), this.stopIfUnobserved();
		};
	}
	stopIfUnobserved() {
		F.read(() => {
			!this.changeSubscriber && !this.events.change?.getSize() && this.stop();
		});
	}
	clearListeners() {
		this.changeSubscriber = void 0;
		for (let e in this.events) this.events[e].clear();
	}
	attach(e, t) {
		this.passiveEffect = e, this.stopPassiveEffect = t;
	}
	set(e) {
		this.passiveEffect ? this.passiveEffect(e, this.updateAndNotify) : this.updateAndNotify(e);
	}
	setWithVelocity(e, t, n) {
		this.set(t), this.prev = void 0, this.prevFrameValue = e, this.prevUpdatedAt = this.updatedAt - n;
	}
	jump(e, t = !0) {
		this.updateAndNotify(e), this.prev = e, this.prevUpdatedAt = this.prevFrameValue = void 0, t && this.stop(), this.stopPassiveEffect && this.stopPassiveEffect();
	}
	dirty() {
		this.notifyChange();
	}
	notifyChange() {
		let { current: e, changeSubscriber: t } = this;
		t ? t(e) : this.events.change?.notify(e);
	}
	addDependent(e) {
		this.dependents ||= /* @__PURE__ */ new Set(), this.dependents.add(e);
	}
	removeDependent(e) {
		this.dependents && this.dependents.delete(e);
	}
	get() {
		ni.current && ni.current.push(this);
		let e = this.animation?.liveValue?.();
		return e === void 0 ? this.current : e;
	}
	getCurrent() {
		return this.current;
	}
	getPrevious() {
		return this.prev;
	}
	getVelocity() {
		let e = O.now();
		if (!this.canTrackVelocity || this.prevFrameValue === void 0 || e - this.updatedAt > ei) return 0;
		let t = Math.min(this.updatedAt - this.prevUpdatedAt, ei);
		return /* @__PURE__ */ S(parseFloat(this.current) - parseFloat(this.prevFrameValue), t);
	}
	start(e) {
		return this.stop(), new Promise((t) => {
			this.hasAnimated = !0;
			let n = !1, r;
			r = e(() => {
				n = !0, this.events.animationComplete?.notify(), this.animation === r && this.clearAnimation(), t();
			}), n || (this.animation = r), this.events.animationStart?.notify();
		});
	}
	stop() {
		this.animation && (this.animation.stop(), this.events.animationCancel && this.events.animationCancel.notify()), this.clearAnimation();
	}
	isAnimating() {
		return !!this.animation;
	}
	clearAnimation() {
		this.animation = void 0;
	}
	destroy() {
		this.dependents?.clear(), this.events.destroy?.notify(), this.clearListeners(), this.stop(), this.stopPassiveEffect && this.stopPassiveEffect();
	}
};
function ii(e, t) {
	return new ri(e, t);
}
// node_modules/motion-dom/dist/es/animation/utils/resolve-transition.mjs
function ai(e, t) {
	if (e?.inherit && t) {
		let { inherit: n, ...r } = e;
		return {
			...t,
			...r
		};
	}
	return e;
}
// node_modules/motion-dom/dist/es/animation/utils/get-value-transition.mjs
function oi(e, t) {
	let n = e?.[t] ?? e?.default ?? e;
	return n === e ? n : ai(n, e);
}
// node_modules/motion-dom/dist/es/animation/utils/default-transitions.mjs
var si = {
	type: "spring",
	stiffness: 500,
	damping: 25,
	restSpeed: 10
}, ci = (e) => ({
	type: "spring",
	stiffness: 550,
	damping: e === 0 ? 2 * Math.sqrt(550) : 30,
	restSpeed: 10
}), li = {
	type: "keyframes",
	duration: .8
}, ui = {
	type: "keyframes",
	ease: [
		.25,
		.1,
		.35,
		1
	],
	duration: .3
}, di = (e, { keyframes: t }) => t.length > 2 ? li : B.has(e) ? e.startsWith("scale") ? ci(t[1]) : si : ui, fi = /* @__PURE__ */ new Set([
	"when",
	"delay",
	"delayChildren",
	"staggerChildren",
	"staggerDirection",
	"repeat",
	"repeatType",
	"repeatDelay",
	"from",
	"elapsed"
]);
function pi(e) {
	for (let t in e) if (!fi.has(t)) return !0;
	return !1;
}
// node_modules/motion-dom/dist/es/animation/interfaces/motion-value.mjs
var mi = (e, t, n, r = {}, i, a) => (o) => {
	let s = oi(r, e) || {}, c = s.delay || r.delay || 0, { elapsed: l = 0 } = r;
	l -= /* @__PURE__ */ b(c);
	let u = {
		keyframes: Array.isArray(n) ? n : [null, n],
		ease: "easeOut",
		velocity: t.getVelocity(),
		...s,
		delay: -l,
		onUpdate: (e) => {
			t.set(e), s.onUpdate && s.onUpdate(e);
		},
		onComplete: () => {
			o(), s.onComplete && s.onComplete();
		},
		name: e,
		motionValue: t,
		element: a ? void 0 : i,
		hasOnUpdate: !!s.onUpdate
	};
	pi(s) || Object.assign(u, di(e, u)), u.duration &&= /* @__PURE__ */ b(u.duration), u.repeatDelay &&= /* @__PURE__ */ b(u.repeatDelay), u.from !== void 0 && (u.keyframes[0] = u.from);
	let f = !1;
	if ((u.type === !1 || u.duration === 0 && !u.repeatDelay) && (Tr(u), u.delay === 0 && (f = !0)), (d.instantAnimations || d.skipAnimations || i?.shouldSkipAnimations || s.skipAnimations) && (f = !0, Tr(u), u.delay = 0), u.allowFlatten = !s.type && !s.ease, f && !a && t.get() !== void 0) {
		let e = an(u.keyframes, s);
		if (e !== void 0) {
			F.update(() => {
				u.onUpdate(e), u.onComplete();
			});
			return;
		}
	}
	return s.isSync ? new gn(u) : new Qr(u);
}, hi = /^var\(--(?:([\w-]+)|([\w-]+), ?([a-zA-Z\d ()%#.,-]+))\)/u;
function gi(e) {
	let t = hi.exec(e);
	if (!t) return [,];
	let [, n, r, i] = t;
	return [`--${n ?? r}`, i];
}
function _i(e, t, n = 1) {
	`${e}`;
	let [r, i] = gi(e);
	if (!r) return;
	let a = window.getComputedStyle(t).getPropertyValue(r);
	if (a) {
		let e = a.trim();
		return f(e) ? parseFloat(e) : e;
	}
	return xe(i) ? _i(i, t, n + 1) : i;
}
// node_modules/motion-dom/dist/es/render/utils/resolve-variants.mjs
function vi(e) {
	let t = [{}, {}];
	return e?.values.forEach((e, n) => {
		t[0][n] = e.get(), t[1][n] = e.getVelocity();
	}), t;
}
function yi(e, t, n, r) {
	let i = (t) => typeof t == "function" ? t(n === void 0 ? e.custom : n, ...vi(r)) : t;
	return t = i(t), typeof t == "string" && (t = e.variants && e.variants[t]), i(t);
}
// node_modules/motion-dom/dist/es/render/utils/resolve-dynamic-variants.mjs
function bi(e, t, n) {
	return yi(e.getProps(), t, n, e);
}
var xi = (e, t) => t === "exit" ? e.presenceContext?.custom : void 0, Si = /* @__PURE__ */ new Set([
	"width",
	"height",
	"top",
	"left",
	"right",
	"bottom",
	...z
]), Ci = (e) => Array.isArray(e);
// node_modules/motion-dom/dist/es/render/utils/setters.mjs
function wi(e, t, n) {
	e.hasValue(t) ? e.getValue(t).set(n) : e.addValue(t, ii(n));
}
function Ti(e) {
	return Ci(e) ? e[e.length - 1] || 0 : e;
}
function Ei(e, t) {
	let { transitionEnd: n = {}, transition: r = {}, ...i } = bi(e, t) || {};
	i = {
		...i,
		...n
	};
	for (let t in i) wi(e, t, Ti(i[t]));
}
// node_modules/motion-dom/dist/es/value/utils/is-motion-value.mjs
var V = (e) => !!(e && e.getVelocity);
// node_modules/motion-dom/dist/es/value/will-change/is.mjs
function Di(e) {
	return !!(V(e) && e.add);
}
// node_modules/motion-dom/dist/es/value/will-change/add-will-change.mjs
function Oi(e, t) {
	let n = e.getValue("willChange");
	if (Di(n)) return n.add(t);
	if (!n && d.WillChange) {
		let n = new d.WillChange("auto");
		e.addValue("willChange", n), n.add(t);
	}
}
// node_modules/motion-dom/dist/es/animation/optimized-appear/data-id.mjs
var ki = "data-framer-appear-id";
// node_modules/motion-dom/dist/es/animation/optimized-appear/get-appear-id.mjs
function Ai(e) {
	return e.props[ki];
}
// node_modules/motion-dom/dist/es/animation/interfaces/visual-element-target.mjs
var ji = typeof window < "u";
function Mi({ protectedKeys: e, needsAnimating: t }, n) {
	let r = n in e && !t[n];
	return t[n] = !1, r;
}
function Ni(e, t, { delay: n = 0, transitionOverride: r, type: i } = {}) {
	let { transition: a, transitionEnd: o, ...s } = t, c = e.getDefaultTransition();
	a = a ? ai(a, c) : c;
	let l = a?.reduceMotion, u = a?.skipAnimations;
	r && (a = r);
	let d = [], f = i && e.animationState?.getState()[i], p = a?.path;
	p && (p.animateValues ? p.animateValues((t) => e.getValue(t, e.latestValues[t] ?? 0), s, a, n, d) : p.animateVisualElement?.(e, s, a, n, d));
	for (let t in s) {
		let r = e.getValue(t, e.latestValues[t] ?? null), i = s[t];
		if (i === void 0 || f && Mi(f, t)) continue;
		let o = {
			delay: n,
			...oi(a || {}, t)
		};
		u && (o.skipAnimations = !0);
		let c = r.get();
		if (c !== void 0 && !r.isAnimating() && !Array.isArray(i) && i === c && !o.velocity) {
			F.update(() => r.set(i));
			continue;
		}
		let p = !1;
		if (ji && window.MotionHandoffAnimation) {
			let n = Ai(e);
			if (n) {
				let e = window.MotionHandoffAnimation(n, t, F);
				e !== null && (o.startTime = e, p = !0);
			}
		}
		Oi(e, t);
		let m = l ?? e.shouldReduceMotion;
		r.start(mi(t, r, i, m && Si.has(t) ? { type: !1 } : o, e, p));
		let h = r.animation;
		h && d.push(h);
	}
	if (o) {
		let t = () => F.update(() => {
			o && Ei(e, o);
		});
		d.length ? Promise.all(d).then(t) : t();
	}
	return d;
}
// node_modules/motion-dom/dist/es/animation/interfaces/visual-element-variant.mjs
function Pi(e, t, n = {}) {
	let r = bi(e, t, xi(e, n.type)), i = n.transitionOverride || (r?.transition ?? (e.getDefaultTransition() || {})), a = () => Promise.all(r ? Ni(e, r, n) : []), o = (r = 0) => {
		let { variantChildren: a } = e, { delayChildren: o = 0, staggerChildren: s, staggerDirection: c } = i, l = [];
		return a?.forEach((e) => {
			e.notify("AnimationStart", t), l.push(Pi(e, t, {
				...n,
				delay: r + (typeof o == "function" ? 0 : o) + $r(a, e, o, s, c)
			}).then(() => e.notify("AnimationComplete", t)));
		}), Promise.all(l);
	}, { when: s } = i;
	return s ? s === "beforeChildren" ? a().then(() => o()) : o().then(a) : Promise.all([a(), o(n.delay)]);
}
// node_modules/motion-dom/dist/es/animation/interfaces/visual-element.mjs
function Fi(e, t, n = {}) {
	return e.notify("AnimationStart", t), (Array.isArray(t) ? Promise.all(t.map((t) => Pi(e, t, n))) : typeof t == "string" ? Pi(e, t, n) : Promise.all(Ni(e, bi(e, t, n.custom), n))).then(() => {
		e.notify("AnimationComplete", t);
	});
}
// node_modules/motion-dom/dist/es/value/types/auto.mjs
var Ii = {
	test: (e) => e === "auto",
	parse: (e) => e
}, Li = (e) => (t) => t.test(e), Ri = [
	we,
	j,
	A,
	k,
	ze,
	Re,
	Ii
], zi = (e) => Ri.find(Li(e));
// node_modules/motion-dom/dist/es/animation/keyframes/utils/is-none.mjs
function Bi(e) {
	return typeof e == "number" ? e === 0 : e === null || e === "none" || e === "0" || m(e);
}
// node_modules/motion-dom/dist/es/animation/keyframes/utils/make-none-animatable.mjs
var Vi = /* @__PURE__ */ new Set([
	"auto",
	"none",
	"0"
]);
function Hi(e, t, n) {
	let r = 0, i;
	for (; r < e.length && !i;) {
		let t = e[r];
		typeof t == "string" && !Vi.has(t) && Qe(t) && (i = e[r]), r++;
	}
	if (i && n) for (let r of t) e[r] !== i && (e[r] = Dn(n, i));
}
// node_modules/motion-dom/dist/es/animation/keyframes/DOMKeyframesResolver.mjs
var Ui = class extends ir {
	constructor(e, t, n, r, i) {
		super(e, t, n, r, i, !0);
	}
	scheduleResolve() {
		if (this.canReadNow() && (this.readKeyframes(), !this.needsMeasurement)) return this.complete();
		super.scheduleResolve();
	}
	canReadNow() {
		let { unresolvedKeyframes: e, element: t, motionValue: n } = this, r = e[0] ?? n?.get();
		if (!t?.current || r === void 0) return !1;
		for (let t = 0; t < e.length; t++) if (Ce(t ? e[t] : r)) return !1;
		return !0;
	}
	readKeyframes() {
		let { unresolvedKeyframes: e, element: t, name: n } = this;
		if (!t || !t.current) return;
		super.readKeyframes();
		for (let n = 0; n < e.length; n++) {
			let r = e[n];
			if (typeof r == "string" && (r = r.trim(), xe(r))) {
				let i = _i(r, t.current);
				i !== void 0 && (e[n] = i), n === e.length - 1 && (this.finalKeyframe = r);
			}
		}
		if (this.resolveNoneKeyframes(), !Si.has(n) || e.length !== 2) return;
		let [r, i] = e;
		if (typeof r == "number" && typeof i == "number") return;
		let a = zi(r), o = zi(i);
		if (Ce(r) !== Ce(i) && Yn[n]) this.needsMeasurement = !0;
		else if (a !== o) {
			if (Vn(a) && Vn(o)) for (let t = 0; t < e.length; t++) {
				let n = e[t];
				typeof n == "string" && (e[t] = parseFloat(n));
			}
			else Yn[n] && (this.needsMeasurement = !0);
		}
	}
	resolveNoneKeyframes() {
		let { unresolvedKeyframes: e, name: t } = this, n = [];
		for (let t = 0; t < e.length; t++) (e[t] === null || Bi(e[t])) && n.push(t);
		n.length && Hi(e, n, t);
	}
	measure() {
		let { element: e, name: t } = this;
		return Yn[t](window.getComputedStyle(e.current), () => e.measureViewportBox());
	}
	measureInitialState() {
		let { element: e, unresolvedKeyframes: t, name: n } = this;
		if (!e || !e.current) return;
		n === "height" && (this.suspendedScrollY = window.pageYOffset), this.measuredOrigin = this.measure(), t[0] = this.measuredOrigin;
		let r = t[t.length - 1];
		r !== void 0 && this.motionValue?.jump(r, !1);
	}
	measureEndState() {
		let { element: e, unresolvedKeyframes: t } = this;
		if (!e || !e.current) return;
		this.motionValue?.jump(this.measuredOrigin, !1);
		let n = t.length - 1, r = t[n];
		t[n] = this.measure(), r !== null && this.finalKeyframe === void 0 && (this.finalKeyframe = r), this.removedTransforms?.length && this.removedTransforms.forEach(([t, n]) => {
			e.getValue(t).set(n);
		}), this.resolveNoneKeyframes();
	}
}, Wi = [
	"borderTopLeftRadius",
	"borderTopRightRadius",
	"borderBottomRightRadius",
	"borderBottomLeftRadius"
];
// node_modules/motion-dom/dist/es/utils/is-html-element.mjs
function Gi(e) {
	return p(e) && "offsetHeight" in e && !("ownerSVGElement" in e);
}
// node_modules/motion-dom/dist/es/utils/is-svg-element.mjs
function Ki(e) {
	return p(e) && "ownerSVGElement" in e;
}
// node_modules/motion-dom/dist/es/scope/active.mjs
var qi = {};
function Ji(e) {
	return qi.current?.add(e), e;
}
var Yi = (e) => qi.current ? qi.current.bind(e) : e;
// node_modules/motion-dom/dist/es/utils/resolve-elements.mjs
function Xi(e, t, n) {
	if (e == null) return [];
	if (e instanceof EventTarget) return [e];
	if (typeof e == "string") {
		let r = t ? t.current : qi.current?.root || document, i = n?.[e] ?? r.querySelectorAll(e);
		return i ? Array.from(i) : [];
	}
	return Array.from(e).filter((e) => e != null);
}
// node_modules/motion-dom/dist/es/render/dom/utils/camel-to-dash.mjs
function Zi(e) {
	return e.replace(/([A-Z])/g, (e) => `-${e.toLowerCase()}`);
}
// node_modules/motion-dom/dist/es/render/html/utils/build-styles.mjs
function Qi(e, t, n) {
	let { style: r, vars: i, transformOrigin: a } = e, o = !1, s = !1;
	for (let e in t) {
		let n = t[e];
		if (B.has(e)) o = !0;
		else if (ye(e)) i[e] = n;
		else {
			let t = Mr(n, Cn[e]);
			e.startsWith("origin") ? (s = !0, a[e] = t) : r[e] = t;
		}
	}
	if (t.transform || (o || n ? r.transform = Fr(t, e.transform, n) : r.transform &&= "none"), s) {
		let { originX: e = "50%", originY: t = "50%", originZ: n = 0 } = a;
		r.transformOrigin = `${e} ${t} ${n}`;
	}
}
// node_modules/motion-dom/dist/es/render/svg/utils/path.mjs
var $i = {
	offset: "stroke-dashoffset",
	array: "stroke-dasharray"
}, ea = {
	offset: "strokeDashoffset",
	array: "strokeDasharray"
};
function ta(e, t, n = 1, r = 0, i = !0) {
	e.pathLength = 1;
	let a = i ? $i : ea;
	e[a.offset] = `${-r}`, e[a.array] = `${t} ${n}`;
}
// node_modules/motion-dom/dist/es/render/svg/utils/build-attrs.mjs
var na = [
	"transform",
	"opacity",
	"clipPath",
	"filter",
	"offsetDistance",
	"offsetPath",
	"offsetRotate",
	"offsetAnchor"
];
function ra(e, { attrX: t, attrY: n, attrScale: r, pathLength: i, pathSpacing: a = 1, pathOffset: o = 0, ...s }, c, l, u) {
	if (Qi(e, s, l), c) {
		e.style.viewBox && (e.attrs.viewBox = e.style.viewBox);
		return;
	}
	e.attrs = e.style, e.style = {};
	let { attrs: d, style: f } = e;
	for (let e of na) d[e] !== void 0 && (f[e] = d[e], delete d[e]);
	(f.transform || d.transformOrigin) && (f.transformOrigin = d.transformOrigin ?? "50% 50%", delete d.transformOrigin), f.transform && (f.transformBox = u?.transformBox ?? "fill-box", delete d.transformBox), t !== void 0 && (d.x = t), n !== void 0 && (d.y = n), r !== void 0 && (d.scale = r), i !== void 0 && ta(d, i, a, o, !1);
}
// node_modules/motion-dom/dist/es/projection/geometry/conversion.mjs
function ia({ top: e, left: t, right: n, bottom: r }) {
	return {
		x: {
			min: t,
			max: n
		},
		y: {
			min: e,
			max: r
		}
	};
}
function aa({ x: e, y: t }) {
	return {
		top: t.min,
		right: e.max,
		bottom: t.max,
		left: e.min
	};
}
function oa(e, t) {
	if (!t) return e;
	let n = t({
		x: e.left,
		y: e.top
	}), r = t({
		x: e.right,
		y: e.bottom
	});
	return {
		top: n.y,
		left: n.x,
		bottom: r.y,
		right: r.x
	};
}
// node_modules/motion-dom/dist/es/projection/utils/has-transform.mjs
function sa(e) {
	return e === void 0 || e === 1;
}
function ca({ scale: e, scaleX: t, scaleY: n }) {
	return !sa(e) || !sa(t) || !sa(n);
}
function la(e) {
	return ca(e) || ua(e) || e.z || e.rotate || e.rotateX || e.rotateY || e.rotateZ || e.skewX || e.skewY;
}
function ua(e) {
	return da(e.x) || da(e.y);
}
function da(e) {
	return e && e !== "0%";
}
// node_modules/motion-dom/dist/es/projection/geometry/delta-apply.mjs
function fa(e, t, n) {
	return n + t * (e - n);
}
function pa(e, t, n, r, i) {
	return i !== void 0 && (e = fa(e, i, r)), fa(e, n, r) + t;
}
function ma(e, t = 0, n = 1, r, i) {
	e.min = pa(e.min, t, n, r, i), e.max = pa(e.max, t, n, r, i);
}
function ha(e, { x: t, y: n }) {
	ma(e.x, t.translate, t.scale, t.originPoint), ma(e.y, n.translate, n.scale, n.originPoint);
}
var ga = .999999999999, _a = 1.0000000000001;
function va(e, t, n, r = !1) {
	let i = n.length;
	if (!i) return;
	t.x = t.y = 1;
	let a, o;
	for (let s = 0; s < i; s++) {
		a = n[s], o = a.projectionDelta;
		let { visualElement: i } = a.options;
		i && i.props.style && i.props.style.display === "contents" || (r && a.options.layoutScroll && a.scroll && a !== a.root && (H(e.x, -a.scroll.offset.x), H(e.y, -a.scroll.offset.y)), o && (t.x *= o.x.scale, t.y *= o.y.scale, ha(e, o)), r && la(a.latestValues) && xa(e, a.latestValues, a.layout?.layoutBox));
	}
	t.x < _a && t.x > ga && (t.x = 1), t.y < _a && t.y > ga && (t.y = 1);
}
function H(e, t) {
	e.min += t, e.max += t;
}
function ya(e, t, n, r, i = .5) {
	ma(e, t, n, P(e.min, e.max, i), r);
}
function ba(e, t) {
	return typeof e == "string" ? parseFloat(e) / 100 * (t.max - t.min) : e;
}
function xa(e, t, n) {
	let r = n ?? e;
	ya(e.x, ba(t.x, r.x), t.scaleX, t.scale, t.originX), ya(e.y, ba(t.y, r.y), t.scaleY, t.scale, t.originY);
}
// node_modules/motion-dom/dist/es/projection/utils/measure.mjs
function Sa(e, t) {
	return ia(oa(e.getBoundingClientRect(), t));
}
function Ca(e, t, n) {
	let r = Sa(e, n), { scroll: i } = t;
	return i && (H(r.x, i.offset.x), H(r.y, i.offset.y)), r;
}
// node_modules/motion-dom/dist/es/frameloop/microtask.mjs
var { schedule: wa, cancel: Ta } = /* @__PURE__ */ Ot(queueMicrotask, !1);
// node_modules/motion-dom/dist/es/animation/animate/single-value.mjs
function Ea(e, t, n) {
	let r = V(e) ? e : ii(e);
	return r.start(mi("", r, t, n)), r.animation;
}
// node_modules/motion-dom/dist/es/projection/styles/scale-correction.mjs
var Da = {}, Oa = {
	value: null,
	addProjectionMetrics: null
};
// node_modules/motion-dom/dist/es/utils/delay.mjs
function ka(e, t) {
	let n = O.now();
	e = Yi(e);
	let r = ({ timestamp: i }) => {
		let a = i - n;
		a >= t && (I(r), e(a - t));
	};
	return F.setup(r, !0), Ji(() => I(r));
}
// node_modules/motion-dom/dist/es/utils/is-svg-svg-element.mjs
function Aa(e) {
	return Ki(e) && e.tagName === "svg";
}
// node_modules/motion-dom/dist/es/value/utils/resolve-motion-value.mjs
function ja(e) {
	return V(e) ? e.get() : e;
}
// node_modules/motion-dom/dist/es/value/utils/as-number.mjs
var Ma = (e) => typeof e == "number" ? e : parseFloat(e), Na = Wi.length, Pa = [
	"rotate",
	"rotateX",
	"rotateY",
	"rotateZ",
	"skewX",
	"skewY"
], Fa = Pa.length, Ia = (e) => typeof e == "number" || j.test(e);
function La(e, t, n, r, i, a) {
	i ? (e.opacity = P(0, n.opacity ?? 1, za(r)), e.opacityExit = P(t.opacity ?? 1, 0, Ba(r))) : a && (e.opacity = P(t.opacity ?? 1, n.opacity ?? 1, r));
	for (let i = 0; i < Na; i++) {
		let a = Wi[i], o = Ra(t, a), s = Ra(n, a);
		(o !== void 0 || s !== void 0) && (o ||= 0, s ||= 0, o === 0 || s === 0 || Ia(o) === Ia(s) ? (e[a] = Math.max(P(Ma(o), Ma(s), r), 0), (A.test(s) || A.test(o)) && (e[a] += "%")) : e[a] = s);
	}
	for (let i = 0; i < Fa; i++) {
		let a = Pa[i];
		(t[a] || n[a]) && (e[a] = P(t[a] || 0, n[a] || 0, r));
	}
}
function Ra(e, t) {
	return e[t] === void 0 ? e.borderRadius : e[t];
}
var za = /*@__PURE__*/ Va(0, .5, ae), Ba = /*@__PURE__*/ Va(.5, .95, g);
function Va(e, t, n) {
	return (r) => r < e ? 0 : r > t ? 1 : n(/* @__PURE__ */ v(e, t, r));
}
// node_modules/motion-dom/dist/es/projection/geometry/copy.mjs
function Ha(e, t) {
	e.min = t.min, e.max = t.max;
}
function U(e, t) {
	Ha(e.x, t.x), Ha(e.y, t.y);
}
function Ua(e, t) {
	e.translate = t.translate, e.scale = t.scale, e.originPoint = t.originPoint, e.origin = t.origin;
}
// node_modules/motion-dom/dist/es/projection/geometry/delta-calc.mjs
var Wa = .9999, Ga = 1.0001, Ka = -.01, qa = .01;
function W(e) {
	return e.max - e.min;
}
function Ja(e, t, n) {
	return Math.abs(e - t) <= n;
}
function Ya(e, t, n, r = .5) {
	e.origin = r, e.originPoint = P(t.min, t.max, e.origin), e.scale = W(n) / W(t), e.translate = P(n.min, n.max, e.origin) - e.originPoint, (e.scale >= Wa && e.scale <= Ga || isNaN(e.scale)) && (e.scale = 1), (e.translate >= Ka && e.translate <= qa || isNaN(e.translate)) && (e.translate = 0);
}
function Xa(e, t, n, r) {
	Ya(e.x, t.x, n.x, r ? r.originX : void 0), Ya(e.y, t.y, n.y, r ? r.originY : void 0);
}
function Za(e, t, n, r = 0) {
	e.min = (r ? P(n.min, n.max, r) : n.min) + t.min, e.max = e.min + W(t);
}
function Qa(e, t, n, r) {
	Za(e.x, t.x, n.x, r?.x), Za(e.y, t.y, n.y, r?.y);
}
function $a(e, t, n, r = 0) {
	let i = r ? P(n.min, n.max, r) : n.min;
	e.min = t.min - i, e.max = e.min + W(t);
}
function eo(e, t, n, r) {
	$a(e.x, t.x, n.x, r?.x), $a(e.y, t.y, n.y, r?.y);
}
// node_modules/motion-dom/dist/es/projection/geometry/delta-remove.mjs
function to(e, t, n, r, i) {
	return e -= t, e = fa(e, 1 / n, r), i !== void 0 && (e = fa(e, 1 / i, r)), e;
}
function no(e, t = 0, n = 1, r = .5, i, a = e, o = e) {
	if (A.test(t) && (t = parseFloat(t), t = P(o.min, o.max, t / 100) - o.min), typeof t != "number") return;
	let s = P(a.min, a.max, r);
	e === a && (s -= t), e.min = to(e.min, t, n, s, i), e.max = to(e.max, t, n, s, i);
}
function ro(e, t, [n, r, i], a, o) {
	no(e, t[n], t[r], t[i], t.scale, a, o);
}
var io = [
	"x",
	"scaleX",
	"originX"
], ao = [
	"y",
	"scaleY",
	"originY"
];
function oo(e, t, n, r) {
	ro(e.x, t, io, n ? n.x : void 0, r ? r.x : void 0), ro(e.y, t, ao, n ? n.y : void 0, r ? r.y : void 0);
}
// node_modules/motion-dom/dist/es/projection/geometry/models.mjs
var so = () => ({
	translate: 0,
	scale: 1,
	origin: 0,
	originPoint: 0
}), co = () => ({
	x: so(),
	y: so()
}), lo = () => ({
	min: 0,
	max: 0
}), G = () => ({
	x: lo(),
	y: lo()
});
// node_modules/motion-dom/dist/es/projection/geometry/utils.mjs
function uo(e) {
	return e.translate === 0 && e.scale === 1;
}
function fo(e) {
	return uo(e.x) && uo(e.y);
}
function po(e, t) {
	return e.min === t.min && e.max === t.max;
}
function mo(e, t) {
	return po(e.x, t.x) && po(e.y, t.y);
}
function ho(e, t) {
	return Math.round(e.min) === Math.round(t.min) && Math.round(e.max) === Math.round(t.max);
}
function go(e, t) {
	return ho(e.x, t.x) && ho(e.y, t.y);
}
function _o(e) {
	return W(e.x) / W(e.y);
}
function vo(e, t) {
	return e.translate === t.translate && e.scale === t.scale && e.originPoint === t.originPoint;
}
// node_modules/motion-dom/dist/es/projection/shared/stack.mjs
var yo = class {
	constructor() {
		this.members = [];
	}
	add(e) {
		c(this.members, e);
		for (let t = this.members.length - 1; t >= 0; t--) {
			let n = this.members[t];
			if (n === e || n === this.lead || n === this.prevLead) continue;
			let r = n.instance;
			r && r.isConnected !== !1 || n.snapshot || (l(this.members, n), n.unmount());
		}
		e.scheduleRender();
	}
	remove(e) {
		if (l(this.members, e), e === this.prevLead && (this.prevLead = void 0), e === this.lead) {
			let e = this.members[this.members.length - 1];
			e && this.promote(e);
		}
	}
	relegate(e) {
		for (let t = this.members.indexOf(e) - 1; t >= 0; t--) {
			let e = this.members[t];
			if (e.isPresent !== !1 && e.instance?.isConnected !== !1) return this.promote(e), !0;
		}
		return !1;
	}
	promote(e, t) {
		let n = this.lead;
		if (e !== n && (this.prevLead = n, this.lead = e, e.show(), n)) {
			n.updateSnapshot(), e.scheduleRender();
			let { layoutDependency: r } = n.options, { layoutDependency: i } = e.options;
			(r === void 0 || r !== i) && (e.resumeFrom = n, t && (n.preserveOpacity = !0), n.snapshot && (e.snapshot = n.snapshot, e.snapshot.latestValues = n.animationValues || n.latestValues), e.root?.isUpdating && (e.isLayoutDirty = !0)), e.options.crossfade === !1 && n.hide();
		}
	}
	exitAnimationComplete() {
		this.members.forEach((e) => {
			e.options.onExitComplete?.(), e.resumingFrom?.options.onExitComplete?.();
		});
	}
	scheduleRender() {
		this.members.forEach((e) => e.instance && e.scheduleRender(!1));
	}
	removeLeadSnapshot() {
		this.lead?.snapshot && (this.lead.snapshot = void 0);
	}
};
// node_modules/motion-dom/dist/es/projection/styles/scale-border-radius.mjs
function bo(e, t) {
	return t.max === t.min ? 0 : e / (t.max - t.min) * 100;
}
var xo = { correct: (e, t) => {
	if (!t.target) return e;
	if (typeof e == "string") {
		if (j.test(e)) e = parseFloat(e);
		else return e;
	}
	return `${bo(e, t.target.x)}% ${bo(e, t.target.y)}%`;
} }, So = {
	borderRadius: /* @__PURE__ */ Object.assign({}, xo, { applyTo: /* @__PURE__ */ Wi.slice() }),
	borderTopLeftRadius: xo,
	borderTopRightRadius: xo,
	borderBottomLeftRadius: xo,
	borderBottomRightRadius: xo,
	boxShadow: { correct: (e, { treeScale: t, projectionDelta: n }) => {
		let r = e, i = N.parse(e);
		if (i.length > 5) return r;
		let a = N.createTransformer(e), o = typeof i[0] == "number" ? 0 : 1, s = n.x.scale * t.x, c = n.y.scale * t.y;
		i[0 + o] /= s, i[1 + o] /= c;
		let l = P(s, c, .5);
		return typeof i[2 + o] == "number" && (i[2 + o] /= l), typeof i[3 + o] == "number" && (i[3 + o] /= l), a(i);
	} }
};
// node_modules/motion-dom/dist/es/projection/styles/transform.mjs
function Co(e, t, n) {
	let r = "", i = e.x.translate / t.x, a = e.y.translate / t.y, o = n?.z || 0;
	if ((i || a || o) && (r = `translate3d(${i}px, ${a}px, ${o}px) `), (t.x !== 1 || t.y !== 1) && (r += `scale(${1 / t.x}, ${1 / t.y}) `), n) {
		let { transformPerspective: e, rotate: t, pathRotation: i, rotateX: a, rotateY: o, rotateZ: s, skewX: c, skewY: l } = n;
		e && (r = `perspective(${e}px) ${r}`), t && (r += `rotate(${t}deg) `), i && (r += `rotate(${i}deg) `), a && (r += `rotateX(${a}deg) `), o && (r += `rotateY(${o}deg) `), s && (r += `rotateZ(${s}deg) `), c && (r += `skewX(${c}deg) `), l && (r += `skewY(${l}deg) `);
	}
	let s = e.x.scale * t.x, c = e.y.scale * t.y;
	return (s !== 1 || c !== 1) && (r += `scale(${s}, ${c})`), r || "none";
}
// node_modules/motion-dom/dist/es/projection/utils/each-axis.mjs
function K(e) {
	return [e("x"), e("y")];
}
// node_modules/motion-dom/dist/es/projection/utils/compare-by-depth.mjs
var wo = (e, t) => e.depth - t.depth, To = class {
	constructor() {
		this.children = [], this.isDirty = !1;
	}
	add(e) {
		c(this.children, e), this.isDirty = !0;
	}
	remove(e) {
		l(this.children, e), this.isDirty = !0;
	}
	forEach(e) {
		this.isDirty && this.children.sort(wo), this.isDirty = !1, this.children.forEach(e);
	}
}, Eo = {
	hasAnimatedSinceResize: !0,
	hasEverUpdated: !1
}, Do = {
	nodes: 0,
	calculatedTargetDeltas: 0,
	calculatedProjections: 0
}, Oo = [
	"",
	"X",
	"Y",
	"Z"
], ko = 1e3, Ao = 0;
function jo(e, t, n, r) {
	let { latestValues: i } = t;
	i[e] && (n[e] = i[e], t.setStaticValue(e, 0), r && (r[e] = 0));
}
var Mo = ({ instance: e }) => e && Ir.get(e)?.demote();
function No(e) {
	if (e.hasCheckedOptimisedAppear = !0, e.root === e) return;
	let { visualElement: t } = e.options;
	if (!t) return;
	let n = Ai(t);
	if (window.MotionHasOptimisedAnimation(n, "transform")) {
		let { layout: t, layoutId: r } = e.options;
		window.MotionCancelOptimisedAnimation(n, "transform", F, !(t || r));
	}
	let { parent: r } = e;
	r && !r.hasCheckedOptimisedAppear && No(r);
}
function Po({ attachResizeListener: e, defaultParent: t, measureScroll: n, checkIsScrollRoot: r, resetTransform: i }) {
	return Object.assign(Da, {
		...So,
		...Da
	}), class {
		constructor(e = {}, n = t?.()) {
			this.id = Ao++, this.animationId = 0, this.animationCommitId = 0, this.children = /* @__PURE__ */ new Set(), this.options = {}, this.isTreeAnimating = !1, this.isAnimationBlocked = !1, this.isLayoutDirty = !1, this.isProjectionDirty = !1, this.isSharedProjectionDirty = !1, this.isTransformDirty = !1, this.updateManuallyBlocked = !1, this.updateBlockedByResize = !1, this.isUpdating = !1, this.isSVG = !1, this.needsReset = !1, this.shouldResetTransform = !1, this.hasCheckedOptimisedAppear = !1, this.treeScale = {
				x: 1,
				y: 1
			}, this.eventHandlers = /* @__PURE__ */ new Map(), this.hasTreeAnimated = !1, this.updateScheduled = !1, this.scheduleUpdate = () => this.update(), this.projectionUpdateScheduled = !1, this.checkUpdateFailed = () => {
				this.isUpdating && (this.isUpdating = !1, this.clearAllSnapshots());
			}, this.updateProjection = () => {
				this.projectionUpdateScheduled = !1, Oa.value && (Do.nodes = Do.calculatedTargetDeltas = Do.calculatedProjections = 0), this.nodes.forEach(Lo), this.nodes.forEach(Jo), this.nodes.forEach(Yo), this.nodes.forEach(Ro), Oa.addProjectionMetrics && Oa.addProjectionMetrics(Do);
			}, this.resolvedRelativeTargetAt = 0, this.hasProjected = !1, this.isVisible = !0, this.animationProgress = 0, this.sharedNodes = /* @__PURE__ */ new Map(), this.latestValues = e, this.root = n ? n.root || n : this, this.path = n ? [...n.path, n] : [], this.parent = n, this.depth = n ? n.depth + 1 : 0;
			for (let e = 0; e < this.path.length; e++) this.path[e].shouldResetTransform = !0;
			this.root === this && (this.nodes = new To());
		}
		addEventListener(e, t) {
			return this.eventHandlers.has(e) || this.eventHandlers.set(e, new y()), this.eventHandlers.get(e).add(t);
		}
		notifyListeners(e, ...t) {
			let n = this.eventHandlers.get(e);
			n && n.notify(...t);
		}
		hasListeners(e) {
			return this.eventHandlers.has(e);
		}
		mount(t) {
			if (this.instance) return;
			this.isSVG = Ki(t) && !Aa(t), this.instance = t;
			let { layoutId: n, layout: r, visualElement: i } = this.options;
			if (i && !i.current && i.mount(t), this.root.nodes.add(this), this.parent && this.parent.children.add(this), this.root.hasTreeAnimated && (r || n) && (this.isLayoutDirty = !0), e) {
				let n, r = 0, i = () => this.root.updateBlockedByResize = !1;
				F.read(() => {
					r = window.innerWidth;
				}), e(t, () => {
					let e = window.innerWidth;
					e !== r && (r = e, this.root.updateBlockedByResize = !0, n && n(), n = ka(i, 250), Eo.hasAnimatedSinceResize && (Eo.hasAnimatedSinceResize = !1, this.nodes.forEach(qo)));
				});
			}
			n && this.root.registerSharedNode(n, this), this.addLayoutListener();
		}
		addLayoutListener() {
			let { layoutId: e, layout: t, visualElement: n, animate: r } = this.options;
			this.instance && !this.hasLayoutListener && r !== !1 && n && (e || t) && (this.hasLayoutListener = !0, this.addEventListener("didUpdate", ({ delta: e, hasLayoutChanged: t, hasRelativeLayoutChanged: r, layout: i }) => {
				if (this.isAnimationBlocked) {
					this.target = void 0, this.relativeTarget = void 0;
					return;
				}
				let a = this.options.transition || n.getDefaultTransition() || ts, { onLayoutAnimationStart: o, onLayoutAnimationComplete: s } = n.getProps(), c = !this.targetLayout || !go(this.targetLayout, i), l = !t && r;
				if (this.options.layoutRoot || this.resumeFrom || l || t && (c || !this.currentAnimation)) {
					this.resumeFrom && (this.resumingFrom = this.resumeFrom, this.resumingFrom.resumingFrom = void 0);
					let t = {
						...oi(a, "layout"),
						onPlay: o,
						onComplete: s
					};
					(n.shouldReduceMotion || this.options.layoutRoot) && (t.delay = 0, t.type = !1), this.startAnimation(t), this.setAnimationOrigin(e, l, t.path);
				} else t || qo(this), this.isLead() && this.options.onExitComplete && this.options.onExitComplete();
				this.targetLayout = i;
			}));
		}
		unmount() {
			this.options.layoutId && this.willUpdate(), this.root.nodes.remove(this);
			let e = this.getStack();
			e && e.remove(this), this.parent && this.parent.children.delete(this), this.instance = void 0, this.eventHandlers.clear(), this.hasLayoutListener = !1, I(this.updateProjection);
		}
		blockUpdate() {
			this.updateManuallyBlocked = !0;
		}
		unblockUpdate() {
			this.updateManuallyBlocked = !1;
		}
		isUpdateBlocked() {
			return this.updateManuallyBlocked || this.updateBlockedByResize;
		}
		startUpdate() {
			this.isUpdateBlocked() || (this.isUpdating = !0, this.nodes && this.nodes.forEach(Xo), this.animationId++);
		}
		getTransformTemplate() {
			let { visualElement: e } = this.options;
			return e && e.getProps().transformTemplate;
		}
		willUpdate(e = !0) {
			if (this.root.hasTreeAnimated = !0, this.root.isUpdateBlocked()) {
				this.options.onExitComplete && this.options.onExitComplete();
				return;
			}
			if (window.MotionCancelOptimisedAnimation && !this.hasCheckedOptimisedAppear && No(this), !this.root.isUpdating && this.root.startUpdate(), this.isLayoutDirty) return;
			Mo(this), this.isLayoutDirty = !0;
			for (let e = 0; e < this.path.length; e++) {
				let t = this.path[e];
				t.shouldResetTransform = !0, Mo(t), (typeof t.latestValues.x == "string" || typeof t.latestValues.y == "string") && (t.isLayoutDirty = !0), t.updateScroll("snapshot"), t.options.layoutRoot && t.willUpdate(!1), t.isAnimationBlocked && (t.dragSnapshot ||= t.measure(), this.dragAncestor = t);
			}
			let { layoutId: t, layout: n } = this.options;
			if (t === void 0 && !n) return;
			let r = this.getTransformTemplate();
			this.prevTransformTemplateValue = r ? r(this.latestValues, "") : void 0, this.updateSnapshot(), e && this.notifyListeners("willUpdate");
		}
		update() {
			if (this.updateScheduled = !1, this.isUpdateBlocked()) {
				let e = this.updateBlockedByResize;
				this.unblockUpdate(), this.updateBlockedByResize = !1, this.clearAllSnapshots(), e && this.nodes.forEach(Vo), this.nodes.forEach(Bo);
				return;
			}
			if (this.animationId <= this.animationCommitId) {
				this.nodes.forEach(Ho);
				return;
			}
			this.animationCommitId = this.animationId, this.isUpdating ? (this.isUpdating = !1, this.nodes.forEach(Wo), this.nodes.forEach(Go), this.nodes.forEach(Ko), this.nodes.forEach(Fo), this.nodes.forEach(Io)) : this.nodes.forEach(Ho), this.clearAllSnapshots();
			let e = O.now();
			D.delta = u(0, 1e3 / 60, e - D.timestamp), D.timestamp = e, D.isProcessing = !0, kt.update.process(D), kt.preRender.process(D), kt.render.process(D), D.isProcessing = !1;
		}
		didUpdate() {
			this.updateScheduled || (this.updateScheduled = !0, wa.read(this.scheduleUpdate));
		}
		clearAllSnapshots() {
			this.nodes.forEach(zo), this.sharedNodes.forEach((e, t) => e.members.length ? e.removeLeadSnapshot() : this.sharedNodes.delete(t));
		}
		scheduleUpdateProjection() {
			this.projectionUpdateScheduled || (this.projectionUpdateScheduled = !0, F.preRender(this.updateProjection, !1, !0));
		}
		scheduleCheckAfterUnmount() {
			F.postRender(() => {
				this.isLayoutDirty ? this.root.didUpdate() : this.root.checkUpdateFailed();
			});
		}
		updateSnapshot() {
			!this.snapshot && this.instance && (this.snapshot = this.measure(), this.snapshot && !W(this.snapshot.measuredBox.x) && !W(this.snapshot.measuredBox.y) && (this.snapshot = void 0));
		}
		updateLayout() {
			if (!this.instance || (this.updateScroll(), !(this.options.alwaysMeasureLayout && this.isLead()) && !this.isLayoutDirty)) return;
			if (this.resumeFrom && !this.resumeFrom.instance) for (let e = 0; e < this.path.length; e++) this.path[e].updateScroll();
			let e = this.layout;
			this.layout = this.measure(!1), this.layoutCorrected ||= G(), this.isLayoutDirty = !1, this.projectionDelta = void 0, this.notifyListeners("measure", this.layout.layoutBox);
			let { visualElement: t } = this.options;
			t && t.notify("LayoutMeasure", this.layout.layoutBox, e ? e.layoutBox : void 0);
		}
		updateScroll(e = "measure") {
			let t = !!(this.options.layoutScroll && this.instance);
			if (this.scroll && this.scroll.animationId === this.root.animationId && this.scroll.phase === e && (t = !1), t && this.instance) {
				let t = r(this.instance);
				this.scroll = {
					animationId: this.root.animationId,
					phase: e,
					isRoot: t,
					offset: n(this.instance),
					wasRoot: this.scroll ? this.scroll.isRoot : t
				};
			}
		}
		resetTransform() {
			if (!i) return;
			let e = this.isLayoutDirty || this.shouldResetTransform || this.options.alwaysMeasureLayout;
			e && Mo(this);
			let t = this.projectionDelta && !fo(this.projectionDelta), n = this.getTransformTemplate(), r = n ? n(this.latestValues, "") : void 0, a = r !== this.prevTransformTemplateValue;
			e && this.instance && (t || la(this.latestValues) || a) && (i(this.instance, r), this.shouldResetTransform = !1, this.scheduleRender());
		}
		measure(e = !0) {
			let t = this.measurePageBox(), n = this.removeElementScroll(t);
			return e && (n = this.removeTransform(n)), as(n), {
				animationId: this.root.animationId,
				measuredBox: t,
				layoutBox: n,
				latestValues: {},
				source: this.id
			};
		}
		measurePageBox() {
			let { visualElement: e } = this.options;
			if (!e) return G();
			let t = e.measureViewportBox();
			if (!(this.scroll?.wasRoot || this.path.some(ss))) {
				let { scroll: e } = this.root;
				e && (H(t.x, e.offset.x), H(t.y, e.offset.y));
			}
			return t;
		}
		removeElementScroll(e) {
			let t = G();
			if (U(t, e), this.scroll?.wasRoot) return t;
			for (let n = 0; n < this.path.length; n++) {
				let r = this.path[n], { scroll: i, options: a } = r;
				r !== this.root && i && a.layoutScroll && (i.wasRoot && U(t, e), H(t.x, i.offset.x), H(t.y, i.offset.y));
			}
			return t;
		}
		applyTransform(e, t = !1, n, r) {
			let i = n || G();
			U(i, e);
			for (let e = 0; e < this.path.length; e++) {
				let n = this.path[e];
				!t && n.options.layoutScroll && n.scroll && n !== n.root && (H(i.x, -n.scroll.offset.x), H(i.y, -n.scroll.offset.y)), la(n.latestValues) && xa(i, n.latestValues, n.layout?.layoutBox);
			}
			return !r && la(this.latestValues) && xa(i, this.latestValues, this.layout?.layoutBox), i;
		}
		removeTransform(e) {
			let t = G();
			U(t, e);
			for (let e = 0; e < this.path.length; e++) {
				let n = this.path[e];
				if (!la(n.latestValues)) continue;
				let r;
				n.instance && (ca(n.latestValues) && n.updateSnapshot(), r = G(), U(r, n.measurePageBox())), oo(t, n.latestValues, n.snapshot?.layoutBox, r);
			}
			return la(this.latestValues) && oo(t, this.latestValues), t;
		}
		setTargetDelta(e) {
			this.targetDelta = e, this.root.scheduleUpdateProjection(), this.isProjectionDirty = !0;
		}
		setOptions(e) {
			this.options = {
				...this.options,
				...e,
				crossfade: e.crossfade === void 0 || e.crossfade
			}, this.addLayoutListener();
		}
		clearMeasurements() {
			this.scroll = void 0, this.layout = void 0, this.snapshot = void 0, this.prevTransformTemplateValue = void 0, this.targetDelta = void 0, this.target = void 0, this.isLayoutDirty = !1;
		}
		forceRelativeParentToResolveTarget() {
			this.relativeParent && this.relativeParent.resolvedRelativeTargetAt !== D.timestamp && this.relativeParent.resolveTargetDelta(!0);
		}
		resolveTargetDelta(e = !1) {
			let t = this.getLead();
			this.isProjectionDirty ||= t.isProjectionDirty, this.isTransformDirty ||= t.isTransformDirty, this.isSharedProjectionDirty ||= t.isSharedProjectionDirty;
			let n = !!this.resumingFrom || this !== t;
			if (!(e || n && this.isSharedProjectionDirty || this.isProjectionDirty || this.parent?.isProjectionDirty || this.attemptToResolveRelativeTarget || this.root.updateBlockedByResize)) return;
			let { layout: r, layoutId: i } = this.options;
			if (!this.layout || !(r || i)) return;
			this.resolvedRelativeTargetAt = D.timestamp;
			let a = this.getClosestProjectingParent();
			!this.targetDelta && !this.relativeTarget && (this.options.layoutAnchor !== !1 && a && a.layout ? this.createRelativeTarget(a, this.layout.layoutBox, a.layout.layoutBox) : this.removeRelativeTarget()), (this.relativeTarget || this.targetDelta) && (this.target || (this.target = G(), this.targetWithTransforms = G()), this.relativeTarget && this.relativeTargetOrigin && this.relativeParent && this.relativeParent.target ? (this.forceRelativeParentToResolveTarget(), Qa(this.target, this.relativeTarget, this.relativeParent.target, this.options.layoutAnchor || void 0)) : this.targetDelta ? (this.resumingFrom ? this.applyTransform(this.layout.layoutBox, !1, this.target, !0) : U(this.target, this.layout.layoutBox), ha(this.target, this.targetDelta)) : U(this.target, this.layout.layoutBox), this.attemptToResolveRelativeTarget && (this.attemptToResolveRelativeTarget = !1, this.options.layoutAnchor !== !1 && a && !!a.resumingFrom == !!this.resumingFrom && !a.options.layoutScroll && a.target && this.animationProgress !== 1 ? this.createRelativeTarget(a, this.target, a.target) : this.relativeParent = this.relativeTarget = void 0), Oa.value && Do.calculatedTargetDeltas++);
		}
		getClosestProjectingParent() {
			if (!(!this.parent || ca(this.parent.latestValues) || ua(this.parent.latestValues))) return this.parent.isProjecting() ? this.parent : this.parent.getClosestProjectingParent();
		}
		isProjecting() {
			return !!((this.relativeTarget || this.targetDelta || this.options.layoutRoot) && this.layout);
		}
		createRelativeTarget(e, t, n) {
			this.relativeParent = e, this.forceRelativeParentToResolveTarget(), this.relativeTarget = G(), this.relativeTargetOrigin = G(), eo(this.relativeTargetOrigin, t, n, this.options.layoutAnchor || void 0), U(this.relativeTarget, this.relativeTargetOrigin);
		}
		removeRelativeTarget() {
			this.relativeParent = this.relativeTarget = void 0;
		}
		calcProjection() {
			let e = this.getLead(), t = !!this.resumingFrom || this !== e, n = !0;
			if ((this.isProjectionDirty || this.parent?.isProjectionDirty) && (n = !1), t && (this.isSharedProjectionDirty || this.isTransformDirty) && (n = !1), this.resolvedRelativeTargetAt === D.timestamp && (n = !1), n) return;
			let { layout: r, layoutId: i } = this.options;
			if (this.isTreeAnimating = !!(this.parent && this.parent.isTreeAnimating || this.currentAnimation || this.pendingAnimation), this.isTreeAnimating || (this.targetDelta = this.relativeTarget = void 0), !this.layout || !(r || i)) return;
			U(this.layoutCorrected, this.layout.layoutBox);
			let a = this.treeScale.x, o = this.treeScale.y;
			va(this.layoutCorrected, this.treeScale, this.path, t), e.layout && !e.target && (this.treeScale.x !== 1 || this.treeScale.y !== 1) && (e.target = e.layout.layoutBox, e.targetWithTransforms = G());
			let { target: s } = e;
			s ? (!this.projectionDelta || !this.prevProjectionDelta ? this.createProjectionDeltas() : (Ua(this.prevProjectionDelta.x, this.projectionDelta.x), Ua(this.prevProjectionDelta.y, this.projectionDelta.y)), Xa(this.projectionDelta, this.layoutCorrected, s, this.latestValues), this.treeScale.x === a && this.treeScale.y === o && vo(this.projectionDelta.x, this.prevProjectionDelta.x) && vo(this.projectionDelta.y, this.prevProjectionDelta.y) || (this.hasProjected = !0, this.scheduleRender(), this.notifyListeners("projectionUpdate", s)), Oa.value && Do.calculatedProjections++) : this.prevProjectionDelta && (this.createProjectionDeltas(), this.scheduleRender());
		}
		hide() {
			this.isVisible = !1;
		}
		show() {
			this.isVisible = !0;
		}
		scheduleRender(e = !0) {
			if (this.options.visualElement?.scheduleRender(), e) {
				let e = this.getStack();
				e && e.scheduleRender();
			}
			this.resumingFrom && !this.resumingFrom.instance && (this.resumingFrom = void 0);
		}
		createProjectionDeltas() {
			this.prevProjectionDelta = co(), this.projectionDelta = co(), this.projectionDeltaWithTransform = co();
		}
		setAnimationOrigin(e, t = !1, n) {
			let r = this.snapshot, i = r ? r.latestValues : {}, a = { ...this.latestValues }, o = co();
			this.relativeParent && this.relativeParent.options.layoutRoot || (this.relativeTarget = this.relativeTargetOrigin = void 0), this.attemptToResolveRelativeTarget = !t;
			let s = G(), c = (r ? r.source : void 0) !== (this.layout ? this.layout.source : void 0), l = this.getStack(), u = !l || l.members.length <= 1, d = !(!c || u || this.options.crossfade !== !0 || this.path.some(es));
			this.animationProgress = 0;
			let f, p = n?.interpolateProjection(e);
			this.mixTargetDelta = (t) => {
				let n = t / 1e3, r = p?.(n);
				r ? (o.x.translate = r.x, o.x.scale = P(e.x.scale, 1, n), o.x.origin = e.x.origin, o.x.originPoint = e.x.originPoint, o.y.translate = r.y, o.y.scale = P(e.y.scale, 1, n), o.y.origin = e.y.origin, o.y.originPoint = e.y.originPoint) : (Zo(o.x, e.x, n), Zo(o.y, e.y, n)), this.setTargetDelta(o), this.relativeTarget && this.relativeTargetOrigin && this.layout && this.relativeParent && this.relativeParent.layout && (eo(s, this.layout.layoutBox, this.relativeParent.layout.layoutBox, this.options.layoutAnchor || void 0), $o(this.relativeTarget, this.relativeTargetOrigin, s, n), f && mo(this.relativeTarget, f) && (this.isProjectionDirty = !1), f ||= G(), U(f, this.relativeTarget)), c && (this.animationValues = a, La(a, i, this.latestValues, n, d, u)), r && r.rotate !== void 0 && (this.animationValues ||= a, this.animationValues.pathRotation = r.rotate), this.root.scheduleUpdateProjection(), this.scheduleRender(), this.animationProgress = n;
			}, this.mixTargetDelta(this.options.layoutRoot ? 1e3 : 0);
		}
		startAnimation(e) {
			this.notifyListeners("animationStart"), this.currentAnimation?.stop(), this.resumingFrom?.currentAnimation?.stop(), this.pendingAnimation &&= (I(this.pendingAnimation), void 0), this.pendingAnimation = F.update(() => {
				Eo.hasAnimatedSinceResize = !0, this.motionValue ||= ii(0), this.motionValue.jump(0, !1), this.currentAnimation = Ea(this.motionValue, [0, 1e3], {
					...e,
					velocity: 0,
					isSync: !0,
					onUpdate: (t) => {
						this.mixTargetDelta(t), e.onUpdate && e.onUpdate(t);
					},
					onComplete: () => {
						e.onComplete && e.onComplete(), this.completeAnimation();
					}
				}), fn(this.currentAnimation, this), this.resumingFrom && (this.resumingFrom.currentAnimation = this.currentAnimation), this.pendingAnimation = void 0;
			});
		}
		completeAnimation() {
			this.resumingFrom && (this.resumingFrom.currentAnimation = void 0, this.resumingFrom.preserveOpacity = void 0);
			let e = this.getStack();
			e && e.exitAnimationComplete(), this.resumingFrom = this.currentAnimation = this.animationValues = void 0, this.notifyListeners("animationComplete");
		}
		finishAnimation() {
			this.currentAnimation && (this.mixTargetDelta && this.mixTargetDelta(ko), this.currentAnimation.stop()), this.completeAnimation();
		}
		applyTransformsToTarget() {
			let e = this.getLead(), { targetWithTransforms: t, layout: n, latestValues: r } = e, { target: i } = e;
			if (t && i && n) {
				if (this !== e && this.layout && n && os(this.options.animationType, this.layout.layoutBox, n.layoutBox)) {
					i = this.target || G();
					let t = W(this.layout.layoutBox.x);
					i.x.min = e.target.x.min, i.x.max = i.x.min + t;
					let n = W(this.layout.layoutBox.y);
					i.y.min = e.target.y.min, i.y.max = i.y.min + n;
				}
				U(t, i), xa(t, r), Xa(this.projectionDeltaWithTransform, this.layoutCorrected, t, r);
			}
		}
		registerSharedNode(e, t) {
			this.sharedNodes.has(e) || this.sharedNodes.set(e, new yo()), this.sharedNodes.get(e).add(t);
			let n = t.options.initialPromotionConfig;
			t.promote({
				transition: n ? n.transition : void 0,
				preserveFollowOpacity: n && n.shouldPreserveFollowOpacity ? n.shouldPreserveFollowOpacity(t) : void 0
			});
		}
		isLead() {
			let e = this.getStack();
			return !e || e.lead === this;
		}
		getLead() {
			let { layoutId: e } = this.options;
			return e && this.getStack()?.lead || this;
		}
		getPrevLead() {
			let { layoutId: e } = this.options;
			return e ? this.getStack()?.prevLead : void 0;
		}
		getStack() {
			let { layoutId: e } = this.options;
			if (e) return this.root.sharedNodes.get(e);
		}
		promote({ needsReset: e, transition: t, preserveFollowOpacity: n } = {}) {
			let r = this.getStack();
			r && r.promote(this, n), e && (this.projectionDelta = void 0, this.needsReset = !0), t && this.setOptions({ transition: t });
		}
		relegate() {
			let e = this.getStack();
			return e ? e.relegate(this) : !1;
		}
		resetSkewAndRotation() {
			let { visualElement: e } = this.options;
			if (!e) return;
			let t = !1, { latestValues: n } = e;
			if ((n.z || n.rotate || n.rotateX || n.rotateY || n.rotateZ || n.skewX || n.skewY) && (t = !0), !t) return;
			let r = {};
			n.z && jo("z", e, r, this.animationValues);
			for (let t = 0; t < Oo.length; t++) jo(`rotate${Oo[t]}`, e, r, this.animationValues), jo(`skew${Oo[t]}`, e, r, this.animationValues);
			e.render();
			for (let t in r) e.setStaticValue(t, r[t]), this.animationValues && (this.animationValues[t] = r[t]);
			e.scheduleRender();
		}
		applyProjectionStyles(e, t) {
			if (!this.instance || this.isSVG) return;
			if (!this.isVisible) {
				e.visibility = "hidden";
				return;
			}
			let n = this.getTransformTemplate();
			if (this.needsReset) {
				this.needsReset = !1, e.visibility = "", e.opacity = "", e.pointerEvents = ja(t?.pointerEvents) || "", e.transform = n ? n(this.latestValues, "") : "none";
				return;
			}
			let r = this.getLead();
			if (!this.projectionDelta || !this.layout || !r.target) {
				this.options.layoutId && (e.opacity = this.latestValues.opacity === void 0 ? 1 : this.latestValues.opacity, e.pointerEvents = ja(t?.pointerEvents) || ""), this.hasProjected && !la(this.latestValues) && (e.transform = n ? n({}, "") : "none", this.hasProjected = !1);
				return;
			}
			e.visibility = "";
			let i = r.animationValues || r.latestValues;
			this.applyTransformsToTarget();
			let a = Co(this.projectionDeltaWithTransform, this.treeScale, i);
			n && (a = n(i, a)), e.transform = a;
			let { x: o, y: s } = this.projectionDelta;
			e.transformOrigin = `${o.origin * 100}% ${s.origin * 100}% 0`, e.opacity = r.animationValues ? r === this ? i.opacity ?? this.latestValues.opacity ?? 1 : this.preserveOpacity ? this.latestValues.opacity : i.opacityExit : r === this ? i.opacity === void 0 ? "" : i.opacity : i.opacityExit === void 0 ? 0 : i.opacityExit;
			for (let t in Da) {
				if (i[t] === void 0) continue;
				let { correct: n, applyTo: o, isCSSVariable: s } = Da[t], c = a === "none" ? i[t] : n(i[t], r);
				if (o) {
					let t = o.length;
					for (let n = 0; n < t; n++) e[o[n]] = c;
				} else s ? this.options.visualElement.renderState.vars[t] = c : e[t] = c;
			}
			this.options.layoutId && (e.pointerEvents = r === this ? ja(t?.pointerEvents) || "" : "none");
		}
		clearSnapshot() {
			this.resumeFrom = this.snapshot = this.dragSnapshot = this.dragAncestor = void 0;
		}
		resetTree() {
			this.root.nodes.forEach((e) => e.currentAnimation?.stop()), this.root.nodes.forEach(Bo), this.root.sharedNodes.clear();
		}
	};
}
function Fo(e) {
	e.updateLayout(), Uo(e);
}
function Io(e) {
	let t = e.resumeFrom?.snapshot || e.snapshot;
	if (e.isLead() && e.layout && t && e.hasListeners("didUpdate")) {
		let { layoutBox: n, measuredBox: r } = e.layout, { animationType: i } = e.options, a = t.source !== e.layout.source;
		if (i === "size") K((e) => {
			let r = a ? t.measuredBox[e] : t.layoutBox[e], i = W(r);
			r.min = n[e].min, r.max = r.min + i;
		});
		else if (i === "x" || i === "y") {
			let e = i === "x" ? "y" : "x";
			Ha(a ? t.measuredBox[e] : t.layoutBox[e], n[e]);
		} else os(i, t.layoutBox, n) && K((r) => {
			let i = a ? t.measuredBox[r] : t.layoutBox[r], o = W(n[r]);
			i.max = i.min + o, e.relativeTarget && !e.currentAnimation && (e.isProjectionDirty = !0, e.relativeTarget[r].max = e.relativeTarget[r].min + o);
		});
		let o = co();
		Xa(o, n, t.layoutBox);
		let s = co();
		a ? Xa(s, e.applyTransform(r, !0), t.measuredBox) : Xa(s, n, t.layoutBox);
		let c = !fo(o), l = !1;
		if (!e.resumeFrom) {
			let r = e.getClosestProjectingParent();
			if (r && !r.resumeFrom) {
				let { snapshot: i, layout: a } = r;
				if (i && a) {
					let o = e.options.layoutAnchor || void 0, s = G();
					eo(s, t.layoutBox, i.layoutBox, o);
					let c = G();
					eo(c, n, a.layoutBox, o), go(s, c) || (l = !0), r.options.layoutRoot && (e.relativeTarget = c, e.relativeTargetOrigin = s, e.relativeParent = r);
				}
			}
		}
		e.notifyListeners("didUpdate", {
			layout: n,
			snapshot: t,
			delta: s,
			layoutDelta: o,
			hasLayoutChanged: c,
			hasRelativeLayoutChanged: l
		});
	} else if (e.isLead()) {
		let { onExitComplete: t } = e.options;
		t && t();
	}
	e.options.transition = void 0;
}
function Lo(e) {
	Oa.value && Do.nodes++, e.parent && (e.isProjecting() || (e.isProjectionDirty = e.parent.isProjectionDirty), e.isSharedProjectionDirty ||= !!(e.isProjectionDirty || e.parent.isProjectionDirty || e.parent.isSharedProjectionDirty), e.isTransformDirty ||= e.parent.isTransformDirty);
}
function Ro(e) {
	e.isProjectionDirty = e.isSharedProjectionDirty = e.isTransformDirty = !1;
}
function zo(e) {
	e.clearSnapshot();
}
function Bo(e) {
	e.clearMeasurements();
}
function Vo(e) {
	e.isLayoutDirty = !0, e.updateLayout();
}
function Ho(e) {
	e.isLayoutDirty = !1;
}
function Uo(e) {
	let { snapshot: t, dragAncestor: n } = e, r = n?.isAnimationBlocked && n.layout;
	if (!t || !r || e.resumeFrom || e.isAnimationBlocked) return;
	let i = n.dragSnapshot;
	K((e) => H(t.layoutBox[e], r.layoutBox[e].min - i.layoutBox[e].min));
}
function Wo(e) {
	e.isAnimationBlocked && e.layout && !e.isLayoutDirty && (e.snapshot = e.dragSnapshot || e.layout, e.isLayoutDirty = !0);
}
function Go(e) {
	e.relativeTarget && e.relativeParent?.isLayoutDirty && (e.currentAnimation ? e.isLayoutDirty = !0 : (e.targetDelta = void 0, e.removeRelativeTarget()));
}
function Ko(e) {
	let { visualElement: t } = e.options;
	t && t.getProps().onBeforeLayoutMeasure && t.notify("BeforeLayoutMeasure"), e.resetTransform();
}
function qo(e) {
	e.finishAnimation(), e.targetDelta = e.relativeTarget = e.target = void 0, e.isProjectionDirty = !0;
}
function Jo(e) {
	e.resolveTargetDelta();
}
function Yo(e) {
	e.calcProjection();
}
function Xo(e) {
	e.resetSkewAndRotation();
}
function Zo(e, t, n) {
	e.translate = P(t.translate, 0, n), e.scale = P(t.scale, 1, n), e.origin = t.origin, e.originPoint = t.originPoint;
}
function Qo(e, t, n, r) {
	e.min = P(t.min, n.min, r), e.max = P(t.max, n.max, r);
}
function $o(e, t, n, r) {
	Qo(e.x, t.x, n.x, r), Qo(e.y, t.y, n.y, r);
}
function es(e) {
	return e.animationValues && e.animationValues.opacityExit !== void 0;
}
var ts = {
	duration: .45,
	ease: [
		.4,
		0,
		.1,
		1
	]
}, ns = (e) => typeof navigator < "u" && navigator.userAgent && navigator.userAgent.toLowerCase().includes(e), rs = ns("applewebkit/") && !ns("chrome/") ? Math.round : g;
function is(e) {
	e.min = rs(e.min), e.max = rs(e.max);
}
function as(e) {
	is(e.x), is(e.y);
}
function os(e, t, n) {
	return e === "position" || e === "preserve-aspect" && !Ja(_o(t), _o(n), .2);
}
function ss(e) {
	return e !== e.root && e.scroll?.wasRoot;
}
// node_modules/motion-dom/dist/es/events/add-dom-event.mjs
function cs(e, t, n, r = { passive: !0 }) {
	return e.addEventListener(t, n, r), () => e.removeEventListener(t, n, r);
}
// node_modules/motion-dom/dist/es/projection/node/DocumentProjectionNode.mjs
var ls = /* @__PURE__ */ Po({
	attachResizeListener: (e, t) => cs(e, "resize", t),
	measureScroll: () => ({
		x: document.documentElement.scrollLeft || document.body?.scrollLeft || 0,
		y: document.documentElement.scrollTop || document.body?.scrollTop || 0
	}),
	checkIsScrollRoot: () => !0
}), us = { current: void 0 }, ds = /* @__PURE__ */ Po({
	measureScroll: (e) => ({
		x: e.scrollLeft,
		y: e.scrollTop
	}),
	defaultParent: () => {
		if (!us.current) {
			let e = new ls({});
			e.mount(window), e.setOptions({ layoutScroll: !0 }), us.current = e;
		}
		return us.current;
	},
	resetTransform: (e, t) => {
		e.style.transform = t === void 0 ? "none" : t;
	},
	checkIsScrollRoot: (e) => window.getComputedStyle(e).position === "fixed"
}), fs = /* @__PURE__ */ new WeakMap();
// node_modules/motion-dom/dist/es/render/utils/is-animation-controls.mjs
function ps(e) {
	return typeof e == "object" && !!e && typeof e.start == "function";
}
// node_modules/motion-dom/dist/es/render/utils/is-variant-label.mjs
function ms(e) {
	return typeof e == "string" || Array.isArray(e);
}
// node_modules/motion-dom/dist/es/render/utils/variant-props.mjs
var hs = [
	"animate",
	"whileInView",
	"whileFocus",
	"whileHover",
	"whileTap",
	"whileDrag",
	"exit"
], gs = /* @__PURE__ */ ["initial"].concat(hs);
// node_modules/motion-dom/dist/es/render/utils/is-controlling-variants.mjs
function _s(e) {
	if (ps(e.animate)) return !0;
	for (let t = 0; t < gs.length; t++) if (ms(e[gs[t]])) return !0;
	return !1;
}
function vs(e) {
	return !!(_s(e) || e.variants);
}
// node_modules/motion-dom/dist/es/render/utils/motion-values.mjs
function ys(e, t, n) {
	for (let r in t) {
		let i = t[r], a = n[r];
		if (V(i)) e.addValue(r, i);
		else if (V(a)) e.addValue(r, ii(i, { owner: e }));
		else if (a !== i) {
			if (e.hasValue(r)) {
				let t = e.getValue(r);
				t.liveStyle === !0 ? t.jump(i) : t.hasAnimated || t.set(i);
			} else {
				let t = e.getStaticValue(r);
				e.addValue(r, ii(t === void 0 ? i : t, { owner: e }));
			}
		}
	}
	for (let r in n) t[r] === void 0 && e.removeValue(r);
	return t;
}
// node_modules/motion-dom/dist/es/render/utils/reduced-motion/state.mjs
var bs = { current: null }, xs = { current: !1 }, Ss = typeof window < "u";
function Cs() {
	if (xs.current = !0, Ss) {
		if (window.matchMedia) {
			let e = window.matchMedia("(prefers-reduced-motion)"), t = () => bs.current = e.matches;
			e.addEventListener("change", t), t();
		} else bs.current = !1;
	}
}
// node_modules/motion-dom/dist/es/render/VisualElement.mjs
var ws = [
	"AnimationStart",
	"AnimationComplete",
	"Update",
	"BeforeLayoutMeasure",
	"LayoutMeasure",
	"LayoutAnimationStart",
	"LayoutAnimationComplete"
], Ts = {};
function Es(e) {
	Ts = e;
}
function Ds() {
	return Ts;
}
var Os = class {
	scrapeMotionValuesFromProps(e, t, n) {
		return {};
	}
	constructor({ parent: e, props: t, presenceContext: n, reducedMotionConfig: r, skipAnimations: i, blockInitialAnimation: a, visualState: o }, s = {}) {
		this.current = null, this.children = /* @__PURE__ */ new Set(), this.isVariantNode = !1, this.isControllingVariants = !1, this.shouldReduceMotion = null, this.shouldSkipAnimations = !1, this.values = /* @__PURE__ */ new Map(), this.KeyframeResolver = ir, this.features = {}, this.valueSubscriptions = /* @__PURE__ */ new Map(), this.prevMotionValues = {}, this.hasBeenMounted = !1, this.events = {}, this.propEventSubscriptions = {}, this.notifyUpdate = () => this.notify("Update", this.latestValues), this.render = () => {
			this.current && (this.triggerBuild(), this.renderInstance(this.current, this.renderState, this.props.style, this.projection));
		}, this.renderScheduledAt = 0, this.scheduleRender = () => {
			let e = O.now();
			this.renderScheduledAt < e && (this.renderScheduledAt = e, F.render(this.render, !1, !0));
		};
		let { latestValues: c, renderState: l } = o;
		this.latestValues = c, this.baseTarget = { ...c }, this.initialValues = t.initial ? { ...c } : {}, this.renderState = l, this.parent = e, this.props = t, this.presenceContext = n, this.depth = e ? e.depth + 1 : 0, this.reducedMotionConfig = r, this.skipAnimationsConfig = i, this.options = s, this.blockInitialAnimation = !!a, this.isControllingVariants = _s(t), this.isVariantNode = vs(t), this.isVariantNode && (this.variantChildren = /* @__PURE__ */ new Set()), this.manuallyAnimateOnMount = !!(e && e.current);
		let { willChange: u, ...d } = this.scrapeMotionValuesFromProps(t, {}, this);
		for (let e in d) {
			let t = d[e];
			c[e] !== void 0 && V(t) && t.set(c[e]);
		}
	}
	mount(e) {
		if (this.hasBeenMounted) for (let e in this.initialValues) this.values.get(e)?.jump(this.initialValues[e]), this.latestValues[e] = this.initialValues[e];
		if (this.current = e, fs.set(e, this), this.projection && !this.projection.instance && this.projection.mount(e), this.isVariantNode && !this.isControllingVariants) {
			let e = this;
			do
				e = e.props.inherit !== !1 && e.parent;
			while (e && !e.isVariantNode);
			if (e) {
				let { variantChildren: t } = e;
				t.add(this), this.removeFromVariantTree = () => t.delete(this);
			}
		}
		this.values.forEach((e, t) => this.bindToMotionValue(t, e)), this.reducedMotionConfig === "never" ? this.shouldReduceMotion = !1 : this.reducedMotionConfig === "always" ? this.shouldReduceMotion = !0 : (xs.current || Cs(), this.shouldReduceMotion = bs.current), this.shouldSkipAnimations = this.skipAnimationsConfig ?? !1, this.parent?.addChild(this), this.update(this.props, this.presenceContext), this.hasBeenMounted = !0;
	}
	unmount() {
		this.projection && this.projection.unmount(), I(this.notifyUpdate), I(this.render), this.valueSubscriptions.forEach((e) => e()), this.valueSubscriptions.clear(), this.removeFromVariantTree && this.removeFromVariantTree(), this.parent?.removeChild(this);
		for (let e in this.events) this.events[e].clear();
		for (let e in this.features) {
			let t = this.features[e];
			t && (t.unmount(), t.isMounted = !1);
		}
		this.current = null;
	}
	addChild(e) {
		this.children.add(e), this.enteringChildren ??= /* @__PURE__ */ new Set(), this.enteringChildren.add(e);
	}
	removeChild(e) {
		this.children.delete(e), this.enteringChildren && this.enteringChildren.delete(e);
	}
	bindToMotionValue(e, t) {
		if (this.valueSubscriptions.has(e) && this.valueSubscriptions.get(e)(), t.accelerate && Er.has(e) && this.current instanceof HTMLElement) {
			let { factory: n, keyframes: r, times: i, ease: a, duration: o } = t.accelerate, s = new _r({
				element: this.current,
				name: e,
				keyframes: r,
				times: i,
				ease: a,
				duration: /* @__PURE__ */ b(o)
			}), c = n(s);
			this.valueSubscriptions.set(e, () => {
				c(), s.cancel();
			});
			return;
		}
		let n = B.has(e);
		n && this.onBindTransform && this.onBindTransform();
		let r = t.on("change", (t) => {
			this.latestValues[e] = t, this.props.onUpdate && F.preRender(this.notifyUpdate), n && (this.projection && (this.projection.isTransformDirty = !0), Ir.get(this.current)?.check()), this.scheduleRender();
		}), i;
		typeof window < "u" && window.MotionCheckAppearSync && (i = window.MotionCheckAppearSync(this, e, t)), this.valueSubscriptions.set(e, () => {
			r(), i && i();
		});
	}
	sortNodePosition(e) {
		return !this.current || !e.current || !this.sortInstanceNodePosition || this.type !== e.type ? 0 : this.sortInstanceNodePosition(this.current, e.current);
	}
	updateFeatures() {
		let e = "animation";
		for (e in Ts) {
			let t = Ts[e];
			if (!t) continue;
			let { isEnabled: n, Feature: r } = t;
			if (!this.features[e] && r && n(this.props) && (this.features[e] = new r(this)), this.features[e]) {
				let t = this.features[e];
				t.isMounted ? t.update() : (t.mount(), t.isMounted = !0);
			}
		}
	}
	triggerBuild() {
		this.build(this.renderState, this.latestValues, this.props);
	}
	measureViewportBox() {
		return this.current ? this.measureInstanceViewportBox(this.current, this.props) : G();
	}
	getStaticValue(e) {
		return this.latestValues[e];
	}
	setStaticValue(e, t) {
		this.latestValues[e] = t;
	}
	update(e, t) {
		let { transformTemplate: n, onUpdate: r } = this.props;
		(e.transformTemplate || n) && this.scheduleRender(), (e.transformTemplate && !n || e.onUpdate && !r) && Ir.get(this.current)?.demote(), this.prevProps = this.props, this.props = e, this.prevPresenceContext = this.presenceContext, this.presenceContext = t;
		for (let t = 0; t < ws.length; t++) {
			let n = ws[t];
			this.propEventSubscriptions[n] && (this.propEventSubscriptions[n](), delete this.propEventSubscriptions[n]);
			let r = e["on" + n];
			r && (this.propEventSubscriptions[n] = this.on(n, r));
		}
		this.prevMotionValues = ys(this, this.scrapeMotionValuesFromProps(e, this.prevProps || {}, this), this.prevMotionValues), this.handleChildMotionValue && this.handleChildMotionValue();
	}
	getProps() {
		return this.props;
	}
	getVariant(e) {
		return this.props.variants ? this.props.variants[e] : void 0;
	}
	getDefaultTransition() {
		return this.props.transition;
	}
	getTransformPagePoint() {
		return this.props.transformPagePoint;
	}
	addValue(e, t) {
		let n = this.values.get(e);
		if (t !== n) {
			n && this.removeValue(e), this.bindToMotionValue(e, t), this.values.set(e, t);
			let r = t.get();
			r !== void 0 && (this.latestValues[e] = r);
		}
	}
	removeValue(e) {
		this.values.delete(e);
		let t = this.valueSubscriptions.get(e);
		t && (t(), this.valueSubscriptions.delete(e)), delete this.latestValues[e], this.removeValueFromRenderState(e, this.renderState);
	}
	hasValue(e) {
		return this.values.has(e);
	}
	getValue(e, t) {
		if (this.props.values && this.props.values[e]) return this.props.values[e];
		let n = this.values.get(e);
		return n === void 0 && t !== void 0 && (n = ii(t ?? this.getDefaultValue?.(e), { owner: this }), this.addValue(e, n)), n;
	}
	readValue(e, t) {
		let n = this.latestValues[e] !== void 0 || !this.current ? this.latestValues[e] : this.getBaseTargetFromProps(this.props, e) ?? this.readValueFromInstance(this.current, e, this.options);
		return n != null && (typeof n == "string" && (f(n) || m(n)) ? n = parseFloat(n) : typeof n != "number" && !N.test(n) && N.test(t) && (n = Dn(e, t)), this.setBaseTarget(e, V(n) ? n.get() : n)), V(n) ? n.get() : n;
	}
	setBaseTarget(e, t) {
		this.baseTarget[e] = t;
	}
	on(e, t) {
		return this.events[e] || (this.events[e] = new y()), this.events[e].add(t);
	}
	notify(e, ...t) {
		this.events[e] && this.events[e].notify(...t);
	}
	scheduleRenderMicrotask() {
		wa.render(this.render);
	}
}, ks = class extends Os {
	constructor() {
		super(...arguments), this.KeyframeResolver = Ui;
	}
	sortInstanceNodePosition(e, t) {
		return e.compareDocumentPosition(t) & 2 ? 1 : -1;
	}
	getBaseTargetFromProps(e, t) {
		let n = e.style;
		return n ? n[t] : void 0;
	}
	removeValueFromRenderState(e, { vars: t, style: n }) {
		delete t[e], delete n[e];
	}
	handleChildMotionValue() {
		this.childSubscription && (this.childSubscription(), delete this.childSubscription);
		let { children: e } = this.props;
		V(e) && (this.childSubscription = e.on("change", (e) => {
			this.current && (this.current.textContent = `${e}`);
		}));
	}
};
// node_modules/motion-dom/dist/es/render/html/utils/render.mjs
function As(e, { style: t, vars: n }, r, i) {
	let a = e.style, o;
	for (o in t) a[o] = t[o];
	for (o in i?.applyProjectionStyles(a, r), n) a.setProperty(o, n[o]);
}
// node_modules/motion-dom/dist/es/render/utils/is-forced-motion-value.mjs
function js(e, { layout: t, layoutId: n }) {
	return B.has(e) || e.startsWith("origin") || (t || n !== void 0) && (!!Da[e] || e === "opacity");
}
// node_modules/motion-dom/dist/es/render/html/utils/scrape-motion-values.mjs
function Ms(e, t, n) {
	let r = e.style, i = t?.style, a = {};
	if (!r) return a;
	for (let t in r) (V(r[t]) || i && V(i[t]) || js(t, e) || n?.getValue(t)?.liveStyle !== void 0) && (a[t] = r[t]);
	return a;
}
// node_modules/motion-dom/dist/es/render/html/HTMLVisualElement.mjs
function Ns(e) {
	return window.getComputedStyle(e);
}
var Ps = class extends ks {
	constructor() {
		super(...arguments), this.type = "html", this.renderInstance = As;
	}
	mount(e) {
		e.style, super.mount(e);
	}
	readValueFromInstance(e, t) {
		if (B.has(t)) return this.projection?.isProjecting ? Ln(t) : zn(e, t);
		{
			let n = Ns(e), r = (ye(t) ? n.getPropertyValue(t) : n[t]) || 0;
			return typeof r == "string" ? r.trim() : r;
		}
	}
	measureInstanceViewportBox(e, { transformPagePoint: t }) {
		return Sa(e, t);
	}
	build(e, t, n) {
		Qi(e, t, n.transformTemplate);
	}
	scrapeMotionValuesFromProps(e, t, n) {
		return Ms(e, t, n);
	}
}, Fs = (e) => e.pointerType === "mouse" ? typeof e.button != "number" || e.button <= 0 : e.isPrimary !== !1;
// node_modules/motion-dom/dist/es/events/event-info.mjs
function Is(e) {
	return { point: {
		x: e.pageX,
		y: e.pageY
	} };
}
var Ls = (e) => (t) => Fs(t) && e(t, Is(t));
// node_modules/motion-dom/dist/es/events/add-pointer-event.mjs
function Rs(e, t, n, r) {
	return cs(e, t, Ls(n), r);
}
// node_modules/motion-dom/dist/es/resize/handle-element.mjs
var zs = /* @__PURE__ */ new WeakMap(), Bs, Vs = (e, t, n) => (r, i) => i && i[0] ? i[0][e + "Size"] : Ki(r) && "getBBox" in r ? r.getBBox()[t] : r[n], Hs = /*@__PURE__*/ Vs("inline", "width", "offsetWidth"), Us = /*@__PURE__*/ Vs("block", "height", "offsetHeight");
function Ws({ target: e, borderBoxSize: t }) {
	zs.get(e)?.forEach((n) => {
		n(e, {
			get width() {
				return Hs(e, t);
			},
			get height() {
				return Us(e, t);
			}
		});
	});
}
function Gs(e) {
	e.forEach(Ws);
}
function Ks() {
	typeof ResizeObserver < "u" && (Bs = new ResizeObserver(Gs));
}
function qs(e, t) {
	Bs || Ks();
	let n = Xi(e);
	return n.forEach((e) => {
		let n = zs.get(e);
		n || (n = /* @__PURE__ */ new Set(), zs.set(e, n)), n.add(t), Bs?.observe(e);
	}), () => {
		n.forEach((e) => {
			let n = zs.get(e);
			n?.delete(t), n?.size || Bs?.unobserve(e);
		});
	};
}
// node_modules/motion-dom/dist/es/resize/handle-window.mjs
var Js = /* @__PURE__ */ new Set(), Ys;
function Xs() {
	Ys = () => {
		let e = {
			get width() {
				return window.innerWidth;
			},
			get height() {
				return window.innerHeight;
			}
		};
		Js.forEach((t) => t(e));
	}, window.addEventListener("resize", Ys);
}
function Zs(e) {
	return Js.add(e), Ys || Xs(), () => {
		Js.delete(e), !Js.size && typeof Ys == "function" && (window.removeEventListener("resize", Ys), Ys = void 0);
	};
}
// node_modules/motion-dom/dist/es/resize/index.mjs
function Qs(e, t) {
	return Ji(typeof e == "function" ? Zs(e) : qs(e, t));
}
// node_modules/motion-dom/dist/es/utils/distance.mjs
var $s = (e, t) => Math.abs(e - t);
function ec(e, t) {
	let n = $s(e.x, t.x), r = $s(e.y, t.y);
	return Math.sqrt(n ** 2 + r ** 2);
}
// node_modules/motion-dom/dist/es/gestures/pan/PanSession.mjs
var tc = /*#__PURE__*/ new Set(["auto", "scroll"]), nc = class {
	constructor(e, t, { transformPagePoint: n, contextWindow: r = window, dragSnapToOrigin: i = !1, distanceThreshold: a = 3, element: o } = {}) {
		if (this.startEvent = null, this.lastMoveEvent = null, this.lastMoveEventInfo = null, this.lastRawMoveEventInfo = null, this.handlers = {}, this.contextWindow = window, this.scrollPositions = /* @__PURE__ */ new Map(), this.removeScrollListeners = null, this.onElementScroll = (e) => {
			this.handleScroll(e.target);
		}, this.onWindowScroll = () => {
			this.handleScroll(window);
		}, this.updatePoint = () => {
			if (!(this.lastMoveEvent && this.lastMoveEventInfo)) return;
			this.hasPendingMove = !1, this.lastRawMoveEventInfo && (this.lastMoveEventInfo = rc(this.lastRawMoveEventInfo, this.transformPagePoint));
			let e = ac(this.lastMoveEventInfo, this.history), t = this.startEvent !== null, n = ec(e.offset, {
				x: 0,
				y: 0
			}) >= this.distanceThreshold;
			if (!t && !n) return;
			let { point: r } = e;
			this.history.push({
				...r,
				timestamp: O.now()
			});
			let { onStart: i, onMove: a } = this.handlers;
			t || (i && i(this.lastMoveEvent, e), this.startEvent = this.lastMoveEvent), a && a(this.lastMoveEvent, e);
		}, this.handlePointerMove = (e, t) => {
			this.handleScroll(window), this.lastMoveEvent = e, this.lastRawMoveEventInfo = t, this.lastMoveEventInfo = rc(t, this.transformPagePoint), this.hasPendingMove = !0, F.update(this.updatePoint, !0);
		}, this.handlePointerUp = (e, t) => {
			this.hasPendingMove && this.updatePoint(), this.end();
			let { onEnd: n, onSessionEnd: r, resumeAnimation: i } = this.handlers;
			if ((this.dragSnapToOrigin || !this.startEvent) && i && i(), !(this.lastMoveEvent && this.lastMoveEventInfo)) return;
			let a = ac(e.type === "pointercancel" ? this.lastMoveEventInfo : rc(t, this.transformPagePoint), this.history);
			this.startEvent && n && n(e, a), r && r(e, a);
		}, !Fs(e)) return;
		this.dragSnapToOrigin = i, this.handlers = t, this.transformPagePoint = n, this.distanceThreshold = a, this.contextWindow = r || window;
		let s = rc(Is(e), this.transformPagePoint), { point: c } = s, { timestamp: l } = D;
		this.history = [{
			...c,
			timestamp: l
		}];
		let { onSessionStart: u } = t;
		u && u(e, ac(s, this.history));
		let d = {
			passive: !0,
			capture: !0
		};
		this.removeListeners = _(Rs(this.contextWindow, "pointermove", this.handlePointerMove, d), Rs(this.contextWindow, "pointerup", this.handlePointerUp, d), Rs(this.contextWindow, "pointercancel", this.handlePointerUp, d)), o && this.startScrollTracking(o);
	}
	startScrollTracking(e) {
		let t = e.parentElement;
		for (; t;) {
			let e = getComputedStyle(t);
			(tc.has(e.overflowX) || tc.has(e.overflowY)) && this.scrollPositions.set(t, {
				x: t.scrollLeft,
				y: t.scrollTop
			}), t = t.parentElement;
		}
		this.scrollPositions.set(window, {
			x: window.scrollX,
			y: window.scrollY
		}), window.addEventListener("scroll", this.onElementScroll, { capture: !0 }), window.addEventListener("scroll", this.onWindowScroll), this.removeScrollListeners = () => {
			window.removeEventListener("scroll", this.onElementScroll, { capture: !0 }), window.removeEventListener("scroll", this.onWindowScroll);
		};
	}
	handleScroll(e) {
		let t = this.scrollPositions.get(e);
		if (!t) return;
		let n = e === window, r = n ? {
			x: window.scrollX,
			y: window.scrollY
		} : {
			x: e.scrollLeft,
			y: e.scrollTop
		}, i = {
			x: r.x - t.x,
			y: r.y - t.y
		};
		(i.x !== 0 || i.y !== 0) && (n ? this.lastMoveEventInfo && (this.lastMoveEventInfo.point.x += i.x, this.lastMoveEventInfo.point.y += i.y) : this.history.length > 0 && (this.history[0].x -= i.x, this.history[0].y -= i.y), this.scrollPositions.set(e, r), F.update(this.updatePoint, !0));
	}
	updateHandlers(e) {
		this.handlers = e;
	}
	end() {
		this.removeListeners && this.removeListeners(), this.removeScrollListeners && this.removeScrollListeners(), this.scrollPositions.clear(), I(this.updatePoint);
	}
};
function rc(e, t) {
	return t ? { point: t(e.point) } : e;
}
function ic(e, t) {
	return {
		x: e.x - t.x,
		y: e.y - t.y
	};
}
function ac({ point: e }, t) {
	return {
		point: e,
		delta: ic(e, sc(t)),
		offset: ic(e, oc(t)),
		velocity: cc(t, .1)
	};
}
function oc(e) {
	return e[0];
}
function sc(e) {
	return e[e.length - 1];
}
function cc(e, t) {
	if (e.length < 2) return {
		x: 0,
		y: 0
	};
	let n = e.length - 1, r = null, i = sc(e);
	for (; n >= 0 && (r = e[n], !(i.timestamp - r.timestamp > /* @__PURE__ */ b(t)));) n--;
	if (!r) return {
		x: 0,
		y: 0
	};
	r === e[0] && e.length > 2 && i.timestamp - r.timestamp > /* @__PURE__ */ b(t) * 2 && (r = e[1]);
	let a = /* @__PURE__ */ x(i.timestamp - r.timestamp);
	if (a === 0) return {
		x: 0,
		y: 0
	};
	let o = {
		x: (i.x - r.x) / a,
		y: (i.y - r.y) / a
	};
	return o.x === Infinity && (o.x = 0), o.y === Infinity && (o.y = 0), o;
}
// node_modules/motion-dom/dist/es/gestures/press/utils/is-keyboard-accessible.mjs
var lc = /* @__PURE__ */ new Set([
	"BUTTON",
	"INPUT",
	"SELECT",
	"TEXTAREA",
	"A"
]);
function uc(e) {
	return lc.has(e.tagName) || e.isContentEditable === !0;
}
var dc = /* @__PURE__ */ new Set([
	"INPUT",
	"SELECT",
	"TEXTAREA"
]);
function fc(e) {
	return dc.has(e.tagName) || e.isContentEditable === !0;
}
// node_modules/motion-dom/dist/es/gestures/drag/state/is-active.mjs
var q = {
	x: !1,
	y: !1
};
function pc() {
	return q.x || q.y;
}
// node_modules/motion-dom/dist/es/gestures/drag/state/set-active.mjs
function mc(e) {
	return e === "x" || e === "y" ? q[e] ? null : (q[e] = !0, () => {
		q[e] = !1;
	}) : q.x || q.y ? null : (q.x = q.y = !0, () => {
		q.x = q.y = !1;
	});
}
// node_modules/motion-dom/dist/es/gestures/drag/utils/constraints.mjs
function hc(e, { min: t, max: n }, r) {
	return t !== void 0 && e < t ? e = r ? P(t, e, r.min) : Math.max(e, t) : n !== void 0 && e > n && (e = r ? P(n, e, r.max) : Math.min(e, n)), e;
}
function gc(e, t, n) {
	return {
		min: t === void 0 ? void 0 : e.min + t,
		max: n === void 0 ? void 0 : e.max + n - (e.max - e.min)
	};
}
function _c(e, { top: t, left: n, bottom: r, right: i }) {
	return {
		x: gc(e.x, n, i),
		y: gc(e.y, t, r)
	};
}
function vc(e, t) {
	let n = t.min - e.min, r = t.max - e.max;
	return t.max - t.min < e.max - e.min && ([n, r] = [r, n]), {
		min: n,
		max: r
	};
}
function yc(e, t) {
	return {
		x: vc(e.x, t.x),
		y: vc(e.y, t.y)
	};
}
function bc(e, t) {
	let n = .5, r = W(e), i = W(t);
	return i > r ? n = /* @__PURE__ */ v(t.min, t.max - r, e.min) : r > i && (n = /* @__PURE__ */ v(e.min, e.max - i, t.min)), u(0, 1, n);
}
function xc(e, t) {
	let n = {};
	return t.min !== void 0 && (n.min = t.min - e.min), t.max !== void 0 && (n.max = t.max - e.min), n;
}
var Sc = .35;
function Cc(e = Sc) {
	return e === !1 ? e = 0 : e === !0 && (e = Sc), {
		x: wc(e, "left", "right"),
		y: wc(e, "top", "bottom")
	};
}
function wc(e, t, n) {
	return {
		min: Tc(e, t),
		max: Tc(e, n)
	};
}
function Tc(e, t) {
	return typeof e == "number" ? e : e[t] || 0;
}
// node_modules/motion-dom/dist/es/gestures/drag/VisualElementDragControls.mjs
var Ec = /* @__PURE__ */ new WeakMap(), Dc = class {
	constructor(e, t = () => e.getProps()) {
		this.openDragLock = null, this.isDragging = !1, this.currentDirection = null, this.originPoint = {
			x: 0,
			y: 0
		}, this.constraints = !1, this.hasMutatedConstraints = !1, this.elastic = G(), this.latestPointerEvent = null, this.latestPanInfo = null, this.visualElement = e, this.readProps = t;
	}
	start(e, { snapToCursor: t = !1, distanceThreshold: n } = {}) {
		let { presenceContext: r } = this.visualElement;
		if (r && r.isPresent === !1) return;
		let i = (e) => {
			t && this.snapToCursor(e), this.stopAnimation();
		}, a = (e, t) => {
			let { drag: n, dragPropagation: r, onDragStart: i } = this.getProps();
			if (n && !r && (this.openDragLock && this.openDragLock(), this.openDragLock = mc(n), !this.openDragLock)) return;
			this.latestPointerEvent = e, this.latestPanInfo = t, this.isDragging = !0, this.currentDirection = null, this.resolveConstraints(), this.visualElement.projection && (this.visualElement.projection.isAnimationBlocked = !0, this.visualElement.projection.target = void 0), K((e) => {
				let t = this.getAxisMotionValue(e).get() || 0;
				if (xe(t) && (t = _i(t, this.visualElement.current) || 0), j.test(t)) t = parseFloat(t);
				else if (A.test(t)) {
					let { projection: n } = this.visualElement;
					if (n && n.layout) {
						let r = n.layout.layoutBox[e];
						r && (t = W(r) * (parseFloat(t) / 100));
					}
				}
				this.originPoint[e] = t;
			}), i && F.update(() => i(e, t), !1, !0), Oi(this.visualElement, "transform");
			let { animationState: a } = this.visualElement;
			a && a.setActive("whileDrag", !0);
		}, o = (e, t) => {
			this.latestPointerEvent = e, this.latestPanInfo = t;
			let { dragPropagation: n, dragDirectionLock: r, onDirectionLock: i, onDrag: a } = this.getProps();
			if (!n && !this.openDragLock) return;
			let { offset: o } = t;
			if (r && this.currentDirection === null) {
				if (this.currentDirection = Nc(o), this.currentDirection === null) return;
				i && i(this.currentDirection);
			}
			this.updateAxis("x", t.point, o), this.updateAxis("y", t.point, o), this.visualElement.render(), a && F.update(() => a(e, t), !1, !0);
		}, s = (e, t) => {
			this.latestPointerEvent = e, this.latestPanInfo = t, this.stop(e, t), this.latestPointerEvent = null, this.latestPanInfo = null;
		}, c = () => {
			let { dragSnapToOrigin: e } = this.getProps();
			(e || this.constraints) && this.startAnimation({
				x: 0,
				y: 0
			});
		}, { dragSnapToOrigin: l } = this.getProps();
		this.panSession = new nc(e, {
			onSessionStart: i,
			onStart: a,
			onMove: o,
			onSessionEnd: s,
			resumeAnimation: c
		}, {
			transformPagePoint: this.visualElement.getTransformPagePoint(),
			dragSnapToOrigin: l,
			distanceThreshold: n,
			contextWindow: this.visualElement.current?.ownerDocument.defaultView,
			element: this.visualElement.current
		});
	}
	stop(e, t, n) {
		let r = e || this.latestPointerEvent, i = t || this.latestPanInfo, a = this.isDragging;
		if (this.cancel(), !a || !i || !r) return;
		let { velocity: o } = i;
		this.startAnimation(o, n);
		let { onDragEnd: s } = this.getProps();
		s && F.postRender(() => s(r, i));
	}
	cancel() {
		this.isDragging = !1;
		let { projection: e, animationState: t } = this.visualElement;
		e && (e.isAnimationBlocked = !1), this.endPanSession();
		let { dragPropagation: n } = this.getProps();
		!n && this.openDragLock && (this.openDragLock(), this.openDragLock = null), t && t.setActive("whileDrag", !1);
	}
	endPanSession() {
		this.panSession && this.panSession.end(), this.panSession = void 0;
	}
	updateAxis(e, t, n) {
		let { drag: r } = this.getProps();
		if (!n || !Mc(e, r, this.currentDirection)) return;
		let i = this.getAxisMotionValue(e), a = this.originPoint[e] + n[e];
		this.constraints && this.constraints[e] && (a = hc(a, this.constraints[e], this.elastic[e])), i.set(a);
	}
	resolveConstraints() {
		let { dragConstraints: e, dragElastic: t } = this.getProps(), n = this.visualElement.projection && !this.visualElement.projection.layout ? this.visualElement.projection.measure(!1) : this.visualElement.projection?.layout, r = this.constraints;
		Oc(e) ? this.constraints ||= this.resolveRefConstraints() : this.constraints = e && n ? _c(n.layoutBox, e) : !1, this.elastic = Cc(t), r !== this.constraints && !Oc(e) && n && this.constraints && !this.hasMutatedConstraints && K((e) => {
			this.constraints !== !1 && this.getAxisMotionValue(e) && (this.constraints[e] = xc(n.layoutBox[e], this.constraints[e]));
		});
	}
	resolveRefConstraints() {
		let { dragConstraints: e, onMeasureDragConstraints: t } = this.getProps();
		if (!Oc(e)) return !1;
		let n = kc(e), { projection: r } = this.visualElement;
		if (!r || !r.layout) return !1;
		r.root && (r.root.scroll = void 0, r.root.updateScroll());
		let i = Ca(n, r.root, this.visualElement.getTransformPagePoint()), a = yc(r.layout.layoutBox, i);
		if (t) {
			let e = t(aa(a));
			this.hasMutatedConstraints = !!e, e && (a = ia(e));
		}
		return a;
	}
	startAnimation(e, t) {
		let { drag: n, dragMomentum: r, dragElastic: i, dragTransition: a, dragSnapToOrigin: o, onDragTransitionEnd: s } = this.getProps(), c = this.constraints || {}, l = K((s) => {
			if (!Mc(s, t || n, this.currentDirection)) return;
			let l = c && c[s] || {};
			(o === !0 || o === s) && (l = {
				min: 0,
				max: 0
			});
			let u = i ? 200 : 1e6, d = i ? 40 : 1e7, f = {
				type: "inertia",
				velocity: r ? e[s] : 0,
				bounceStiffness: u,
				bounceDamping: d,
				timeConstant: 750,
				restDelta: 1,
				restSpeed: 10,
				...a,
				...l
			};
			return this.startAxisValueAnimation(s, f);
		});
		return Promise.all(l).then(s);
	}
	startAxisValueAnimation(e, t) {
		let n = this.getAxisMotionValue(e);
		return Oi(this.visualElement, e), n.start(mi(e, n, 0, t, this.visualElement, !1));
	}
	stopAnimation() {
		K((e) => this.getAxisMotionValue(e).stop());
	}
	getAxisMotionValue(e) {
		let t = `_drag${e.toUpperCase()}`;
		return this.readProps()[t] || this.visualElement.getValue(e, this.visualElement.latestValues[e] ?? 0);
	}
	snapToCursor({ clientX: e, clientY: t }) {
		let { drag: n } = this.getProps(), r = {
			x: e,
			y: t
		}, i = this.visualElement.getTransformPagePoint()?.(r) || r, a = this.visualElement.measureViewportBox();
		K((e) => {
			if (!Mc(e, n, this.currentDirection)) return;
			let t = this.getAxisMotionValue(e), { min: r, max: o } = a[e];
			t.set((t.get() || 0) + i[e] - P(r, o, .5));
		});
	}
	scalePositionWithinConstraints() {
		if (!this.visualElement.current) return;
		let { drag: e, dragConstraints: t } = this.getProps(), { projection: n } = this.visualElement;
		if (!Oc(t) || !n || !this.constraints) return;
		this.stopAnimation();
		let r = this.constraints, i = {
			x: 0,
			y: 0
		};
		K((e) => {
			let t = this.getAxisMotionValue(e).get();
			i[e] = bc({
				min: t,
				max: t
			}, r[e]);
		});
		let { transformTemplate: a } = this.visualElement.getProps();
		this.visualElement.current.style.transform = a ? a({}, "") : "none", n.root && n.root.updateScroll(), n.updateLayout(), this.constraints = !1, this.resolveConstraints(), K((t) => {
			let n = this.getAxisMotionValue(t);
			if (!Mc(t, e, null) || !n.get()) return;
			let { min: r, max: a } = this.constraints[t];
			n.set(P(r, a, i[t]));
		}), this.visualElement.render();
	}
	addListeners() {
		if (!this.visualElement.current) return;
		Ec.set(this.visualElement, this);
		let e = this.visualElement.current, t = Rs(e, "pointerdown", (t) => {
			let { drag: n, dragListener: r = !0 } = this.getProps(), i = t.target, a = i !== e && fc(i);
			n && r && !a && this.start(t);
		}), n, r = () => {
			let { dragConstraints: t } = this.getProps(), r = Oc(t) && kc(t);
			r && (this.constraints = this.resolveRefConstraints(), n ||= jc(e, r, () => this.scalePositionWithinConstraints()));
		}, { projection: i } = this.visualElement, a = i.addEventListener("measure", r);
		i && !i.layout && (i.root && i.root.updateScroll(), i.updateLayout()), F.read(r);
		let o = cs(window, "resize", () => this.scalePositionWithinConstraints()), s = i.addEventListener("didUpdate", (({ delta: e, hasLayoutChanged: t }) => {
			this.isDragging && t && (K((t) => {
				if (!Mc(t, this.getProps().drag, this.currentDirection)) return;
				let n = this.getAxisMotionValue(t);
				this.originPoint[t] += e[t].translate, n.set(n.get() + e[t].translate);
			}), this.visualElement.render());
		}));
		return () => {
			o(), t(), a(), s && s(), n && n();
		};
	}
	getProps() {
		let e = this.readProps(), { drag: t = !1, dragDirectionLock: n = !1, dragPropagation: r = !1, dragConstraints: i = !1, dragElastic: a = Sc, dragMomentum: o = !0 } = e;
		return {
			...e,
			drag: t,
			dragDirectionLock: n,
			dragPropagation: r,
			dragConstraints: i,
			dragElastic: a,
			dragMomentum: o
		};
	}
};
function Oc(e) {
	return !!e && typeof e == "object" && ("current" in e || "nodeType" in e);
}
var kc = (e) => "current" in e ? e.current : e;
function Ac(e) {
	let t = !0;
	return () => {
		t ? t = !1 : e();
	};
}
function jc(e, t, n) {
	let r = Qs(e, Ac(n)), i = Qs(t, Ac(n));
	return () => {
		r(), i();
	};
}
function Mc(e, t, n) {
	return (t === !0 || t === e) && (n === null || n === e);
}
function Nc(e, t = 10) {
	let n = null;
	return Math.abs(e.y) > t ? n = "y" : Math.abs(e.x) > t && (n = "x"), n;
}
// node_modules/motion-dom/dist/es/gestures/utils/setup.mjs
function Pc(e, t) {
	let n = Xi(e), r = new AbortController();
	return [
		n,
		{
			passive: !0,
			...t,
			signal: r.signal
		},
		Ji(() => r.abort())
	];
}
// node_modules/motion-dom/dist/es/gestures/hover.mjs
function Fc(e) {
	return !(e.pointerType === "touch" || pc());
}
function Ic(e, t, n = {}) {
	t = Yi(t);
	let [r, i, a] = Pc(e, n);
	return r.forEach((e) => {
		let n = !1, r = !1, a, o = () => {
			e.removeEventListener("pointerleave", u);
		}, s = (e) => {
			a &&= (a(e), void 0), o();
		}, c = (e) => {
			n = !1, window.removeEventListener("pointerup", c), window.removeEventListener("pointercancel", c), r && (r = !1, s(e));
		}, l = () => {
			n = !0, window.addEventListener("pointerup", c, i), window.addEventListener("pointercancel", c, i);
		}, u = (e) => {
			e.pointerType !== "touch" && (n ? r = !0 : s(e));
		};
		e.addEventListener("pointerenter", (n) => {
			if (!Fc(n)) return;
			r = !1;
			let o = t(e, n);
			typeof o == "function" && (a = o, e.addEventListener("pointerleave", u, i));
		}, i), e.addEventListener("pointerdown", l, i);
	}), a;
}
// node_modules/motion-dom/dist/es/gestures/utils/is-node-or-child.mjs
var Lc = (e, t) => t ? e === t || Lc(e, t.parentElement) : !1, Rc = /* @__PURE__ */ new WeakSet();
// node_modules/motion-dom/dist/es/gestures/press/utils/keyboard.mjs
function zc(e) {
	return (t) => {
		t.key === "Enter" && e(t);
	};
}
function Bc(e, t) {
	e.dispatchEvent(new PointerEvent("pointer" + t, {
		isPrimary: !0,
		bubbles: !0
	}));
}
var Vc = (e, t) => {
	let n = !1, r = zc(() => {
		Rc.has(e) || (n = !0, Bc(e, "down"));
	}), i = zc(() => {
		n && (n = !1, Bc(e, "up"));
	});
	e.addEventListener("keydown", r, t), e.addEventListener("keyup", i, t), e.addEventListener("blur", () => {
		e.removeEventListener("keydown", r), e.removeEventListener("keyup", i), n && Bc(e, "cancel");
	}, {
		...t,
		once: !0
	});
};
// node_modules/motion-dom/dist/es/gestures/press/index.mjs
function Hc(e) {
	return Fs(e) && !pc();
}
var Uc = /* @__PURE__ */ new WeakSet();
function Wc(e, t, n = {}) {
	t = Yi(t);
	let [r, i, a] = Pc(e, n), o = (e) => {
		let r = e.currentTarget;
		if (!Hc(e) || Uc.has(e)) return;
		Rc.add(r), n.stopPropagation && Uc.add(e);
		let a = t(r, e), o = {
			...i,
			capture: !0
		}, s = (e, t) => {
			window.removeEventListener("pointerup", c, o), window.removeEventListener("pointercancel", l, o), Rc.has(r) && Rc.delete(r), Hc(e) && typeof a == "function" && a(e, { success: t });
		}, c = (e) => {
			s(e, r === window || r === document || n.useGlobalTarget || Lc(r, e.target));
		}, l = (e) => {
			s(e, !1);
		};
		window.addEventListener("pointerup", c, o), window.addEventListener("pointercancel", l, o);
	};
	return r.forEach((e) => {
		(n.useGlobalTarget ? window : e).addEventListener("pointerdown", o, i), Gi(e) && (e.addEventListener("focus", () => Vc(e, i), i), !uc(e) && !e.hasAttribute("tabindex") && (e.tabIndex = 0));
	}), a;
}
// node_modules/motion-dom/dist/es/render/Feature.mjs
var J = class {
	constructor(e) {
		this.isMounted = !1, this.node = e;
	}
	update() {}
}, Gc = /* @__PURE__ */ new Set([
	"baseFrequency",
	"diffuseConstant",
	"kernelMatrix",
	"kernelUnitLength",
	"keySplines",
	"keyTimes",
	"limitingConeAngle",
	"markerHeight",
	"markerWidth",
	"numOctaves",
	"targetX",
	"targetY",
	"surfaceScale",
	"specularConstant",
	"specularExponent",
	"stdDeviation",
	"tableValues",
	"viewBox",
	"gradientTransform",
	"pathLength",
	"startOffset",
	"textLength",
	"lengthAdjust"
]), Kc = (e) => typeof e == "string" && e.toLowerCase() === "svg";
// node_modules/motion-dom/dist/es/render/svg/utils/render.mjs
function qc(e, t, n, r) {
	As(e, t, void 0, r);
	for (let n in t.attrs) e.setAttribute(Gc.has(n) ? n : Zi(n), t.attrs[n]);
}
// node_modules/motion-dom/dist/es/render/svg/utils/scrape-motion-values.mjs
function Jc(e, t, n) {
	let r = Ms(e, t, n);
	for (let n in e) if (n !== "children" && (V(e[n]) || V(t[n]))) {
		let t = z.indexOf(n) === -1 ? n : "attr" + n.charAt(0).toUpperCase() + n.substring(1);
		r[t] = e[n];
	}
	return r;
}
// node_modules/motion-dom/dist/es/render/svg/SVGVisualElement.mjs
var Yc = class extends ks {
	constructor() {
		super(...arguments), this.type = "svg", this.isSVGTag = !1, this.measureInstanceViewportBox = G;
	}
	getBaseTargetFromProps(e, t) {
		return B.has(t) ? super.getBaseTargetFromProps(e, t) ?? Ln(t) : e[t];
	}
	getDefaultValue(e) {
		return B.has(e) ? this.latestValues[e] ?? Ln(e) : void 0;
	}
	readValueFromInstance(e, t) {
		if (B.has(t)) {
			let e = Tn(t);
			return e && e.default || 0;
		}
		if (na.includes(t)) {
			let n = getComputedStyle(e)[t];
			if (typeof n == "string" && n) return n.trim();
		}
		return t = Gc.has(t) ? t : Zi(t), e.getAttribute(t);
	}
	scrapeMotionValuesFromProps(e, t, n) {
		return Jc(e, t, n);
	}
	build(e, t, n) {
		ra(e, t, this.isSVGTag, n.transformTemplate, n.style);
	}
	renderInstance(e, t, n, r) {
		qc(e, t, n, r);
	}
	mount(e) {
		this.isSVGTag = Kc(e.tagName), super.mount(e);
	}
};
// node_modules/motion-dom/dist/es/render/utils/shallow-compare.mjs
function Xc(e, t) {
	if (!Array.isArray(t)) return !1;
	let n = t.length;
	if (n !== e.length) return !1;
	for (let r = 0; r < n; r++) if (t[r] !== e[r]) return !1;
	return !0;
}
// node_modules/motion-dom/dist/es/render/utils/animation-state.mjs
var Zc = [...hs].reverse();
function Qc(e) {
	let t = (t) => Promise.all(t.map(({ animation: t, options: n }) => Fi(e, t, n))), n = el(), r = !0, i = !1;
	function a(a) {
		let { props: o, parent: s, manuallyAnimateOnMount: c } = e, l = r || i, u = [], d = /* @__PURE__ */ new Set(), f = {}, p = Infinity, m = e;
		do
			m = m.props.inherit !== !1 && m.parent;
		while (m && !m.isControllingVariants);
		if (Zc.forEach((t, r) => {
			let i = n[t], h = o[t], g = m && m.props[t], _ = h === void 0 ? ms(g) || g === !1 ? g : void 0 : h, v = ms(_), y = t === a ? i.isActive : null;
			y === !1 && (p = r);
			let b = v && h === void 0 && !(l && c);
			if (i.protectedKeys = { ...f }, !i.isActive && y === null || !_ && !i.prevProp || ps(_) || typeof _ == "boolean") return;
			if (t === "exit" && i.isActive && y !== !0) {
				f = {
					...f,
					...i.prevResolvedValues
				};
				return;
			}
			let x = $c(i.prevProp, _), S = x || v && (y && !b || r > p), C = !1, w = Array.isArray(_) ? _ : [_], T = {};
			if (y !== !1) for (let n of w) {
				let { transition: r, transitionEnd: i, ...a } = bi(e, n, xi(e, t)) || {};
				T = {
					...T,
					...a,
					...i
				};
			}
			let { prevResolvedValues: ee } = i;
			for (let t in {
				...ee,
				...T
			}) {
				if (t in f) continue;
				let n = T[t], r = ee[t], a = Array.isArray(n) && Array.isArray(r) ? !Xc(n, r) || x : n !== r;
				if (a && n == null) d.add(t);
				else if (a || n !== void 0 && d.has(t)) {
					S = !0, d.delete(t) && (C = !0), i.needsAnimating[t] = !0;
					let n = e.getValue(t);
					n && (n.liveStyle = !1);
				} else i.protectedKeys[t] = !0;
			}
			if (i.prevProp = _, i.prevResolvedValues = T, i.isActive && (f = {
				...f,
				...T
			}), S && (!(b && x) || C)) for (let n of w) {
				let r = { type: t };
				if (typeof n == "string" && l && c && s?.enteringChildren) {
					let t = bi(s, n)?.transition?.delayChildren;
					r.delay = $r(s.enteringChildren, e, t);
				}
				u.push({
					animation: n,
					options: r
				});
			}
		}), d.size) {
			let { initial: t } = o, n = {}, r = typeof t != "boolean" && bi(e, Array.isArray(t) ? t[0] : t, e.presenceContext?.custom);
			r && r.transition && (n.transition = r.transition), d.forEach((i) => {
				let a = e.getValue(i);
				a && (a.liveStyle = !0);
				let s = r && !Array.isArray(t) ? r[i] : void 0, c = e.getBaseTargetFromProps(o, i);
				n[i] = (s === void 0 ? c !== void 0 && !V(c) ? c : e.initialValues[i] === void 0 ? e.baseTarget[i] : void 0 : s) ?? null;
			}), u.push({ animation: n });
		}
		let h = l && e.blockInitialAnimation || r && !c && (o.initial === !1 || o.initial === o.animate);
		return r = i = !1, !h && u.length ? t(u) : Promise.resolve();
	}
	function o(t, r) {
		if (n[t].isActive === r) return Promise.resolve();
		e.variantChildren?.forEach((e) => e.animationState?.setActive(t, r)), n[t].isActive = r;
		let i = a(t);
		for (let e in n) n[e].protectedKeys = {};
		return i;
	}
	return {
		animateChanges: a,
		setActive: o,
		setAnimateFunction: (n) => {
			t = n(e);
		},
		getState: () => n,
		reset: () => {
			n = el(), i = !0;
		}
	};
}
function $c(e, t) {
	return typeof t == "string" ? t !== e : Array.isArray(t) ? !Xc(t, e) : !1;
}
function el() {
	let e = {};
	for (let t of hs) e[t] = {
		isActive: t === "animate",
		protectedKeys: {},
		needsAnimating: {},
		prevResolvedValues: {}
	};
	return e;
}
// node_modules/framer-motion/dist/es/context/MotionConfigContext.mjs
var tl = /* @__PURE__ */ (0, r.createContext)({
	transformPagePoint: (e) => e,
	isStatic: !1,
	reducedMotion: "never"
});
// node_modules/framer-motion/dist/es/components/AnimatePresence/use-presence.mjs
function nl(e = !0) {
	let t = (0, r.useContext)(s);
	if (t === null) return [!0, null];
	let { isPresent: n, onExitComplete: i, register: a } = t, o = (0, r.useId)();
	(0, r.useEffect)(() => {
		if (e) return a(o);
	}, [e]);
	let c = (0, r.useCallback)(() => e && i && i(o), [
		o,
		i,
		e
	]);
	return !n && i ? [!1, c] : [!0];
}
// node_modules/framer-motion/dist/es/context/LazyContext.mjs
var rl = /* @__PURE__ */ (0, r.createContext)({ strict: !1 }), il = {
	animation: [
		"animate",
		"variants",
		"whileHover",
		"whileTap",
		"exit",
		"whileInView",
		"whileFocus",
		"whileDrag"
	],
	exit: ["exit"],
	drag: ["drag", "dragControls"],
	focus: ["whileFocus"],
	hover: [
		"whileHover",
		"onHoverStart",
		"onHoverEnd"
	],
	tap: [
		"whileTap",
		"onTap",
		"onTapStart",
		"onTapCancel"
	],
	pan: [
		"onPan",
		"onPanStart",
		"onPanSessionStart",
		"onPanEnd"
	],
	inView: [
		"whileInView",
		"onViewportEnter",
		"onViewportLeave"
	],
	layout: ["layout", "layoutId"]
}, al = !1;
function ol() {
	if (al) return;
	let e = {};
	for (let t in il) e[t] = { isEnabled: (e) => il[t].some((t) => !!e[t]) };
	Es(e), al = !0;
}
function sl() {
	return ol(), Ds();
}
// node_modules/framer-motion/dist/es/motion/features/load-features.mjs
function cl(e) {
	let t = sl();
	for (let n in e) t[n] = {
		...t[n],
		...e[n]
	};
	Es(t);
}
// node_modules/framer-motion/dist/es/context/MotionContext/index.mjs
var ll = /* @__PURE__ */ (0, r.createContext)({});
// node_modules/framer-motion/dist/es/context/MotionContext/utils.mjs
function ul(e, t) {
	if (_s(e)) {
		let { initial: t, animate: n } = e;
		return {
			initial: t === !1 || ms(t) ? t : void 0,
			animate: ms(n) ? n : void 0
		};
	}
	return e.inherit === !1 ? {} : t;
}
// node_modules/framer-motion/dist/es/context/MotionContext/create.mjs
function dl(e) {
	let { initial: t, animate: n } = ul(e, (0, r.useContext)(ll));
	return (0, r.useMemo)(() => ({
		initial: t,
		animate: n
	}), [fl(t), fl(n)]);
}
function fl(e) {
	return Array.isArray(e) ? e.join(" ") : e;
}
// node_modules/framer-motion/dist/es/render/html/utils/create-render-state.mjs
var pl = () => ({
	style: {},
	transform: {},
	transformOrigin: {},
	vars: {}
});
// node_modules/framer-motion/dist/es/render/html/use-props.mjs
function ml(e, t, n) {
	for (let r in t) !V(t[r]) && !js(r, n) && (e[r] = t[r]);
}
function hl({ transformTemplate: e }, t) {
	return (0, r.useMemo)(() => {
		let n = pl();
		return Qi(n, t, e), Object.assign({}, n.vars, n.style);
	}, [t]);
}
function gl(e, t) {
	let n = e.style || {}, r = {};
	return ml(r, n, e), Object.assign(r, hl(e, t)), r;
}
function _l(e, t) {
	let n = {}, r = gl(e, t);
	return e.drag && e.dragListener !== !1 && (n.draggable = !1, r.userSelect = r.WebkitUserSelect = r.WebkitTouchCallout = "none", r.touchAction = e.drag === !0 ? "none" : `pan-${e.drag === "x" ? "y" : "x"}`), e.tabIndex === void 0 && (e.onTap || e.onTapStart || e.whileTap) && (n.tabIndex = 0), n.style = r, n;
}
// node_modules/framer-motion/dist/es/render/svg/utils/create-render-state.mjs
var vl = () => ({
	...pl(),
	attrs: {}
});
// node_modules/framer-motion/dist/es/render/svg/use-props.mjs
function yl(e, t, n, i) {
	let a = (0, r.useMemo)(() => {
		let n = vl();
		return ra(n, t, Kc(i), e.transformTemplate, e.style), {
			...n.attrs,
			style: { ...n.style }
		};
	}, [t]);
	if (e.style) {
		let t = {};
		return ml(t, e.style, e), {
			...a,
			style: {
				...t,
				...a.style
			}
		};
	}
	return a;
}
// node_modules/framer-motion/dist/es/motion/utils/valid-prop.mjs
var bl = /* @__PURE__ */ new Set(/* @__PURE__ */ "animate.exit.variants.initial.style.values.transition.transformTemplate.custom.inherit.onBeforeLayoutMeasure.onAnimationStart.onAnimationComplete.onUpdate.onDragStart.onDrag.onDragEnd.onMeasureDragConstraints.onDirectionLock.onDragTransitionEnd._dragX._dragY.onHoverStart.onHoverEnd.onViewportEnter.onViewportLeave.globalTapTarget.propagate.ignoreStrict.viewport".split("."));
function xl(e) {
	return e.startsWith("while") || e.startsWith("drag") && e !== "draggable" || e.startsWith("layout") || e.startsWith("onTap") || e.startsWith("onPan") || e.startsWith("onLayout") || bl.has(e);
}
// node_modules/framer-motion/dist/es/render/dom/utils/filter-props.mjs
function Sl(e, t) {
	return e.startsWith("on") ? !xl(e) : t?.(e) ?? !xl(e);
}
function Cl(e, t, n, r) {
	let i = {};
	for (let a in e) a === "values" && typeof e.values == "object" || V(e[a]) || (Sl(a, r) || n === !0 && xl(a) || !t && !xl(a) || e.draggable && a.startsWith("onDrag")) && (i[a] = e[a]);
	return i;
}
// node_modules/framer-motion/dist/es/render/svg/lowercase-elements.mjs
var wl = [
	"animate",
	"circle",
	"defs",
	"desc",
	"ellipse",
	"g",
	"image",
	"line",
	"filter",
	"marker",
	"mask",
	"metadata",
	"path",
	"pattern",
	"polygon",
	"polyline",
	"rect",
	"stop",
	"switch",
	"symbol",
	"svg",
	"text",
	"tspan",
	"use",
	"view"
];
// node_modules/framer-motion/dist/es/render/dom/utils/is-svg-component.mjs
function Tl(e) {
	return typeof e != "string" || e.includes("-") ? !1 : !!(wl.indexOf(e) > -1 || /[A-Z]/u.test(e));
}
// node_modules/framer-motion/dist/es/render/dom/use-render.mjs
function El(e, t, n, { latestValues: i }, a, o = !1, s, c) {
	let l = (s ?? Tl(e) ? yl : _l)(t, i, a, e), u = Cl(t, typeof e == "string", o, c), d = e === r.Fragment ? {} : {
		...u,
		...l,
		ref: n
	}, { children: f } = t, p = (0, r.useMemo)(() => V(f) ? f.get() : f, [f]);
	return (0, r.createElement)(e, {
		...d,
		children: p
	});
}
// node_modules/framer-motion/dist/es/motion/utils/use-visual-state.mjs
function Dl({ scrapeMotionValuesFromProps: e, createRenderState: t }, n, r, i) {
	return {
		latestValues: Ol(n, r, i, e),
		renderState: t()
	};
}
function Ol(e, t, n, r) {
	let i = {}, a = r(e, {});
	for (let e in a) i[e] = ja(a[e]);
	let { initial: o, animate: s } = e, c = _s(e), l = vs(e);
	t && l && !c && e.inherit !== !1 && (o === void 0 && (o = t.initial), s === void 0 && (s = t.animate));
	let u = n ? n.initial === !1 : !1;
	u ||= o === !1;
	let d = u ? s : o;
	if (d && typeof d != "boolean" && !ps(d)) {
		let t = Array.isArray(d) ? d : [d];
		for (let n = 0; n < t.length; n++) {
			let r = yi(e, t[n]);
			if (r) {
				let { transitionEnd: e, transition: t, ...n } = r;
				for (let e in n) {
					let t = n[e];
					if (Array.isArray(t)) {
						let e = u ? t.length - 1 : 0;
						t = t[e];
					}
					t !== null && (i[e] = t);
				}
				for (let t in e) i[t] = e[t];
			}
		}
	}
	return i;
}
var kl = (e) => (t, n) => {
	let i = (0, r.useContext)(ll), o = (0, r.useContext)(s), c = () => Dl(e, t, i, o);
	return n ? c() : a(c);
}, Al = /*@__PURE__*/ kl({
	scrapeMotionValuesFromProps: Ms,
	createRenderState: pl
}), jl = /*@__PURE__*/ kl({
	scrapeMotionValuesFromProps: Jc,
	createRenderState: vl
}), Ml = /* @__PURE__ */ Symbol.for("motionComponentSymbol");
// node_modules/framer-motion/dist/es/motion/utils/use-motion-ref.mjs
function Nl(e, t, n) {
	let i = (0, r.useRef)(n);
	(0, r.useInsertionEffect)(() => {
		i.current = n;
	});
	let a = (0, r.useRef)(null);
	return (0, r.useCallback)((n) => {
		n && e.onMount?.(n), t && (n ? t.mount(n) : t.unmount());
		let r = i.current;
		if (typeof r == "function") {
			if (n) {
				let e = r(n);
				typeof e == "function" && (a.current = e);
			} else a.current ? (a.current(), a.current = null) : r(n);
		} else r && (r.current = n);
	}, [t]);
}
// node_modules/framer-motion/dist/es/context/SwitchLayoutGroupContext.mjs
var Pl = /* @__PURE__ */ (0, r.createContext)({});
// node_modules/framer-motion/dist/es/utils/is-ref-object.mjs
function Fl(e) {
	return e && typeof e == "object" && Object.prototype.hasOwnProperty.call(e, "current");
}
// node_modules/framer-motion/dist/es/motion/utils/use-visual-element.mjs
function Il(e, t, n, i, a, c) {
	let { visualElement: l } = (0, r.useContext)(ll), u = (0, r.useContext)(rl), d = (0, r.useContext)(s), f = (0, r.useContext)(tl), p = f.reducedMotion, m = f.skipAnimations, h = (0, r.useRef)(null), g = (0, r.useRef)(!1);
	i ||= u.renderer, !h.current && i && (h.current = i(e, {
		visualState: t,
		parent: l,
		props: n,
		presenceContext: d,
		blockInitialAnimation: d ? d.initial === !1 : !1,
		reducedMotionConfig: p,
		skipAnimations: m,
		isSVG: c
	}), g.current && h.current && (h.current.manuallyAnimateOnMount = !0));
	let _ = h.current, v = (0, r.useContext)(Pl);
	_ && !_.projection && a && (_.type === "html" || _.type === "svg") && Ll(h.current, n, a, v), (0, r.useInsertionEffect)(() => {
		_ && _.props !== n && _.update(n, d);
	});
	let y = n[ki], b = (0, r.useRef)(!!y && typeof window < "u" && !window.MotionHandoffIsComplete?.(y) && window.MotionHasOptimisedAnimation?.(y));
	return o(() => {
		g.current && _ && (_.animationState?.animateChanges(), _.enteringChildren = void 0);
	}, []), o(() => {
		g.current = !0, _ && (window.MotionIsMounted = !0, _.updateFeatures(), _.scheduleRenderMicrotask(), b.current && _.animationState && _.animationState.animateChanges());
	}), (0, r.useEffect)(() => {
		_ && (!b.current && _.animationState && _.animationState.animateChanges(), b.current &&= (queueMicrotask(() => {
			window.MotionHandoffMarkAsComplete?.(y);
		}), !1), _.enteringChildren = void 0);
	}), _;
}
function Ll(e, t, n, r) {
	let { layoutId: i, layout: a, drag: o, dragConstraints: s, layoutScroll: c, layoutRoot: l, layoutAnchor: u, layoutCrossfade: d } = t, f = e.projection = new n(e.latestValues, t["data-framer-portal-id"] ? void 0 : Rl(e.parent));
	f.setOptions({
		layoutId: i,
		layout: a,
		alwaysMeasureLayout: !!o || s && Fl(s),
		visualElement: e,
		animationType: typeof a == "string" ? a : "both",
		initialPromotionConfig: r,
		crossfade: d,
		layoutScroll: c,
		layoutRoot: l,
		layoutAnchor: u
	});
	let p = f.getStack()?.lead;
	p && !f.root.isUpdateBlocked() && (p.willUpdate(), p.scheduleCheckAfterUnmount());
}
function Rl(e) {
	if (e) return e.options.allowProjection === !1 ? Rl(e.parent) : e.projection;
}
// node_modules/framer-motion/dist/es/motion/index.mjs
var Y = n();
function zl(e, { forwardMotionProps: t = !1, type: n } = {}, i, o) {
	i && cl(i);
	let s = n ? n === "svg" : Tl(e), c = s ? jl : Al;
	function l(n, l) {
		let u, d = Bl(n);
		a(() => !!us.current?.sharedNodes.get(d)?.lead && n.initial) && (n = {
			...n,
			initial: !1
		});
		let f = {
			...(0, r.useContext)(tl),
			...n,
			layoutId: d
		}, { isStatic: p, isValidProp: m } = f, h = dl(n), g = c(n, p);
		if (!p && typeof window < "u") {
			Vl(f, i);
			let t = Hl(f);
			u = t.MeasureLayout, h.visualElement = Il(e, g, f, o, t.ProjectionNode, s);
		}
		return (0, Y.jsxs)(ll.Provider, {
			value: h,
			children: [u && h.visualElement ? (0, Y.jsx)(u, {
				visualElement: h.visualElement,
				...f
			}) : null, El(e, n, Nl(g, h.visualElement, l), g, p, t, s, m)]
		});
	}
	l.displayName = `motion.${typeof e == "string" ? e : `create(${e.displayName ?? e.name ?? ""})`}`;
	let u = (0, r.forwardRef)(l);
	return u[Ml] = e, u;
}
function Bl({ layoutId: e }) {
	let t = (0, r.useContext)(i).id;
	return t && e !== void 0 ? t + "-" + e : e;
}
function Vl(e, t) {
	(0, r.useContext)(rl).strict;
}
function Hl(e) {
	let { drag: t, layout: n } = sl();
	if (!t && !n) return {};
	let r = {
		...t,
		...n
	};
	return {
		MeasureLayout: t?.isEnabled(e) || n?.isEnabled(e) ? r.MeasureLayout : void 0,
		ProjectionNode: r.ProjectionNode
	};
}
// node_modules/framer-motion/dist/es/render/components/create-proxy.mjs
function Ul(e, t) {
	if (typeof Proxy > "u") return zl;
	let n = /* @__PURE__ */ new Map(), r = (n, r) => zl(n, r, e, t);
	return new Proxy((e, t) => r(e, t), { get: (i, a) => a === "create" ? r : (n.has(a) || n.set(a, zl(a, void 0, e, t)), n.get(a)) });
}
// node_modules/framer-motion/dist/es/render/dom/create-visual-element.mjs
var Wl = (e, t) => t.isSVG ?? Tl(e) ? new Yc(t) : new Ps(t, { allowProjection: e !== r.Fragment }), Gl = class extends J {
	constructor(e) {
		super(e), e.animationState ||= Qc(e);
	}
	updateAnimationControlsSubscription() {
		let { animate: e } = this.node.getProps();
		ps(e) && (this.unmountControls = e.subscribe(this.node));
	}
	mount() {
		this.updateAnimationControlsSubscription();
	}
	update() {
		let { animate: e } = this.node.getProps(), { animate: t } = this.node.prevProps || {};
		e !== t && this.updateAnimationControlsSubscription();
	}
	unmount() {
		this.node.animationState.reset(), this.unmountControls?.();
	}
}, Kl = 0, ql = {
	animation: { Feature: Gl },
	exit: { Feature: class extends J {
		constructor() {
			super(...arguments), this.id = Kl++;
		}
		update() {
			let { presenceContext: e, prevPresenceContext: t, animationState: n } = this.node;
			if (!e || !n) return;
			let { isPresent: r, onExitComplete: i } = e, a = t?.isPresent;
			if (r === a) return;
			if (r && a === !1) {
				if (this.exit === !0) {
					let { initial: e } = this.node.getProps();
					if (e && !Array.isArray(e)) {
						let { transition: t, transitionEnd: n, ...r } = bi(this.node, e) || {};
						for (let e in r) this.node.getValue(e)?.jump(r[e]);
					}
					this.node.blockInitialAnimation = !1, n.reset(), n.animateChanges();
				} else n.setActive("exit", !1);
				this.exit = void 0;
				return;
			}
			let o = this.exit = n.setActive("exit", !r);
			i && !r && o.then(() => {
				this.exit === o && (this.exit = !0, i(this.id));
			});
		}
		mount() {
			let { register: e, onExitComplete: t } = this.node.presenceContext || {};
			t && t(this.id), e && (this.unmount = e(this.id, this.node));
		}
		unmount() {}
	} }
}, Jl = class extends J {
	constructor(e) {
		super(e), this.removeGroupControls = g, this.removeListeners = g, this.controls = new Dc(e);
	}
	mount() {
		let { dragControls: e } = this.node.getProps();
		e && (this.removeGroupControls = e.subscribe(this.controls)), this.removeListeners = this.controls.addListeners() || g;
	}
	update() {
		let { dragControls: e, drag: t } = this.node.getProps(), { dragControls: n, drag: r } = this.node.prevProps || {};
		e !== n && (this.removeGroupControls(), e && (this.removeGroupControls = e.subscribe(this.controls))), r && !t && this.controls.stop(void 0, void 0, r);
	}
	unmount() {
		this.removeGroupControls(), this.removeListeners(), this.controls.isDragging || this.controls.endPanSession();
	}
}, Yl = ({ current: e }) => e ? e.ownerDocument.defaultView : null, Xl = (e) => (t, n) => {
	e && F.update(() => e(t, n), !1, !0);
}, Zl = class extends J {
	constructor() {
		super(...arguments), this.removePointerDownListener = g;
	}
	onPointerDown(e) {
		this.session = new nc(e, this.createPanHandlers(), {
			transformPagePoint: this.node.getTransformPagePoint(),
			contextWindow: Yl(this.node)
		});
	}
	createPanHandlers() {
		let { onPanSessionStart: e, onPanStart: t, onPan: n, onPanEnd: r } = this.node.getProps();
		return {
			onSessionStart: Xl(e),
			onStart: Xl(t),
			onMove: Xl(n),
			onEnd: (e, t) => {
				delete this.session, r && F.postRender(() => r(e, t));
			}
		};
	}
	mount() {
		this.removePointerDownListener = Rs(this.node.current, "pointerdown", (e) => this.onPointerDown(e));
	}
	update() {
		this.session && this.session.updateHandlers(this.createPanHandlers());
	}
	unmount() {
		this.removePointerDownListener(), this.session && this.session.end();
	}
}, Ql = !1, $l = ({ layout: e, layoutDependency: t }) => ({
	layout: e,
	layoutDependency: t,
	animationType: typeof e == "string" ? e : "both"
}), eu = class extends r.Component {
	componentDidMount() {
		let { visualElement: e, layoutGroup: t, switchLayoutGroup: n, layoutId: r } = this.props, { projection: i } = e;
		i && (t.group && t.group.add(i), n && n.register && r && n.register(i), Ql && i.root.didUpdate(), i.addEventListener("animationComplete", () => {
			this.safeToRemove();
		}), i.setOptions({
			...i.options,
			...$l(this.props),
			onExitComplete: () => this.safeToRemove()
		})), Eo.hasEverUpdated = !0;
	}
	getSnapshotBeforeUpdate(e) {
		let { layout: t, layoutDependency: n, visualElement: r, drag: i, isPresent: a } = this.props, { projection: o } = r;
		return o && (o.isPresent = a, (e.layoutDependency !== n || e.layout !== t) && o.setOptions({
			...o.options,
			...$l(this.props)
		}), Ql = !0, i || e.layoutDependency !== n || n === void 0 || e.isPresent !== a ? o.willUpdate() : this.safeToRemove(), e.isPresent !== a && (a ? o.promote() : o.relegate() || F.postRender(() => {
			let e = o.getStack();
			e && e.members.length || this.safeToRemove();
		}))), null;
	}
	componentDidUpdate() {
		let { visualElement: e, layoutAnchor: t } = this.props, { projection: n } = e;
		n && (n.options.layoutAnchor = t, n.root.didUpdate(), wa.postRender(() => {
			!n.currentAnimation && n.isLead() && this.safeToRemove();
		}));
	}
	componentWillUnmount() {
		let { visualElement: e, layoutGroup: t, switchLayoutGroup: n } = this.props, { projection: r } = e;
		Ql = !0, r && (r.scheduleCheckAfterUnmount(), t && t.group && t.group.remove(r), n && n.deregister && n.deregister(r));
	}
	safeToRemove() {
		let { safeToRemove: e } = this.props;
		e && e();
	}
	render() {
		return null;
	}
};
function tu(e) {
	let [t, n] = nl(), a = (0, r.useContext)(i);
	return (0, Y.jsx)(eu, {
		...e,
		layoutGroup: a,
		switchLayoutGroup: (0, r.useContext)(Pl),
		isPresent: t,
		safeToRemove: n
	});
}
// node_modules/framer-motion/dist/es/motion/features/drag.mjs
var nu = {
	pan: { Feature: Zl },
	drag: {
		Feature: Jl,
		ProjectionNode: ds,
		MeasureLayout: tu
	}
};
// node_modules/framer-motion/dist/es/gestures/hover.mjs
function ru(e, t, n) {
	let { props: r } = e;
	e.animationState && r.whileHover && e.animationState.setActive("whileHover", n === "Start");
	let i = r["onHover" + n];
	i && F.postRender(() => i(t, Is(t)));
}
var iu = class extends J {
	mount() {
		let { current: e } = this.node;
		e && (this.unmount = Ic(e, (e, t) => (ru(this.node, t, "Start"), (e) => ru(this.node, e, "End"))));
	}
	unmount() {}
}, au = class extends J {
	constructor() {
		super(...arguments), this.isActive = !1;
	}
	onFocus() {
		let e = !1;
		try {
			e = this.node.current.matches(":focus-visible");
		} catch {
			e = !0;
		}
		e && this.node.animationState && (this.node.animationState.setActive("whileFocus", !0), this.isActive = !0);
	}
	onBlur() {
		this.isActive && this.node.animationState && (this.node.animationState.setActive("whileFocus", !1), this.isActive = !1);
	}
	mount() {
		this.unmount = _(cs(this.node.current, "focus", () => this.onFocus()), cs(this.node.current, "blur", () => this.onBlur()));
	}
	unmount() {}
};
// node_modules/framer-motion/dist/es/gestures/press.mjs
function ou(e, t, n) {
	let { props: r } = e;
	if (e.current instanceof HTMLButtonElement && e.current.disabled) return;
	e.animationState && r.whileTap && e.animationState.setActive("whileTap", n === "Start");
	let i = r["onTap" + (n === "End" ? "" : n)];
	i && F.postRender(() => i(t, Is(t)));
}
var su = class extends J {
	mount() {
		let { current: e } = this.node;
		if (!e) return;
		let { globalTapTarget: t, propagate: n } = this.node.props;
		this.unmount = Wc(e, (e, t) => (ou(this.node, t, "Start"), (e, { success: t }) => ou(this.node, e, t ? "End" : "Cancel")), {
			useGlobalTarget: t,
			stopPropagation: n?.tap === !1
		});
	}
	unmount() {}
}, cu = /* @__PURE__ */ new WeakMap(), lu = /* @__PURE__ */ new WeakMap(), uu = (e) => {
	let t = cu.get(e.target);
	t && t(e);
}, du = (e) => {
	e.forEach(uu);
};
function fu({ root: e, ...t }) {
	let n = e || document;
	lu.has(n) || lu.set(n, {});
	let r = lu.get(n), i = JSON.stringify(t);
	return r[i] || (r[i] = new IntersectionObserver(du, {
		root: e,
		...t
	})), r[i];
}
function pu(e, t, n) {
	let r = fu(t);
	return cu.set(e, n), r.observe(e), () => {
		cu.delete(e), r.unobserve(e);
	};
}
// node_modules/framer-motion/dist/es/motion/features/viewport/index.mjs
var mu = {
	some: 0,
	all: 1
}, hu = class extends J {
	constructor() {
		super(...arguments), this.hasEnteredView = !1, this.isInView = !1;
	}
	startObserver() {
		this.stopObserver?.();
		let { viewport: e = {} } = this.node.getProps(), { root: t, margin: n, amount: r = "some", once: i } = e, a = {
			root: t ? t.current : void 0,
			rootMargin: n,
			threshold: typeof r == "number" ? r : mu[r]
		}, o = (e) => {
			let { isIntersecting: t } = e;
			if (this.isInView === t || (this.isInView = t, i && !t && this.hasEnteredView)) return;
			t && (this.hasEnteredView = !0), this.node.animationState && this.node.animationState.setActive("whileInView", t);
			let { onViewportEnter: n, onViewportLeave: r } = this.node.getProps(), a = t ? n : r;
			a && a(e);
		};
		this.stopObserver = pu(this.node.current, a, o);
	}
	mount() {
		this.startObserver();
	}
	update() {
		if (typeof IntersectionObserver > "u") return;
		let { props: e, prevProps: t } = this.node;
		[
			"amount",
			"margin",
			"root"
		].some(gu(e, t)) && this.startObserver();
	}
	unmount() {
		this.stopObserver?.(), this.hasEnteredView = !1, this.isInView = !1;
	}
};
function gu({ viewport: e = {} }, { viewport: t = {} } = {}) {
	return (n) => e[n] !== t[n];
}
// node_modules/motion/dist/es/react.mjs
var _u = /* @__PURE__ */ Ul(/* @__PURE__ */ Object.assign({}, ql, {
	inView: { Feature: hu },
	tap: { Feature: su },
	focus: { Feature: au },
	hover: { Feature: iu }
}, nu, { layout: {
	ProjectionNode: ds,
	MeasureLayout: tu
} }), Wl), X = [
	"#38dda4",
	"#92e8bf",
	"#71a9d3",
	"#c8ba84",
	"#b18dc4",
	"#e48d82"
], vu = /* @__PURE__ */ new Set([
	"home",
	"shop",
	"cart",
	"donate",
	"map",
	"rules",
	"support"
]), yu = {
	home: "Главная",
	shop: "Магазин",
	cart: "Корзина",
	donate: "Донат",
	map: "Карта",
	rules: "Правила",
	support: "Поддержка"
}, bu = {
	page_view: "Просмотры",
	product_view: "Предметы",
	item_view: "Предметы",
	add_to_cart: "Корзина",
	cart_add: "Корзина",
	cart_remove: "Удаления",
	checkout_start: "Оформление",
	checkout_started: "Оформление",
	checkout_complete: "Покупки",
	checkout_completed: "Покупки",
	donation_start: "Пополнение",
	payment_created: "Пополнение",
	download_mods: "Сборка",
	download_launcher: "Лаунчер",
	play: "Вход"
}, Z = (e) => Number.isFinite(Number(e)) ? Math.max(0, Number(e)) : 0, Q = (e) => Math.round(e).toLocaleString("ru-RU"), xu = (e) => typeof e == "number" ? new Date(e < 1e10 ? e * 1e3 : e).toISOString().slice(0, 10) : String(e || "").slice(0, 10);
function Su(e) {
	let t = /* @__PURE__ */ new Map();
	for (let n of e.events || []) {
		if (n.type !== "page_view") continue;
		let e = xu(n.day);
		e && t.set(e, (t.get(e) || 0) + Z(n.count));
	}
	let n = (e.days || []).map((e) => {
		let n = xu(e.at || e.day);
		return {
			label: n,
			value: Z(e.visits ?? t.get(n) ?? 0)
		};
	}).filter((e) => e.label);
	return n.length ? n.sort((e, t) => e.label.localeCompare(t.label)).slice(-90) : [...t].map(([e, t]) => ({
		label: e,
		value: t
	})).sort((e, t) => e.label.localeCompare(t.label));
}
function Cu(e) {
	return (e.pages || []).filter((e) => vu.has(String(e.page))).map((e) => ({
		label: yu[String(e.page)] || String(e.page),
		value: Z(e.count)
	})).filter((e) => e.value > 0).sort((e, t) => t.value - e.value).slice(0, 8);
}
function wu(e) {
	let t = /* @__PURE__ */ new Map();
	for (let n of e.events || []) {
		if (!vu.has(String(n.page))) continue;
		let e = String(n.type || "other"), r = String(n.page || "");
		t.has(e) || t.set(e, /* @__PURE__ */ new Map());
		let i = t.get(e);
		i.set(r, (i.get(r) || 0) + Z(n.count));
	}
	return [...t].map(([e, t]) => ({
		event: e,
		label: bu[e] || e.replace(/_/g, " "),
		pages: [...t].map(([e, t]) => ({
			page: e,
			value: t
		}))
	})).sort((e, t) => t.pages.reduce((e, t) => e + t.value, 0) - e.pages.reduce((e, t) => e + t.value, 0));
}
function Tu({ title: e, detail: t }) {
	return /* @__PURE__ */ (0, Y.jsxs)("div", {
		className: "cm-react-chart-empty",
		children: [/* @__PURE__ */ (0, Y.jsx)("strong", { children: e }), /* @__PURE__ */ (0, Y.jsx)("p", { children: t })]
	});
}
function $({ title: e, children: t, detail: n }) {
	return /* @__PURE__ */ (0, Y.jsxs)("article", {
		className: "cm-react-chart-card",
		children: [
			/* @__PURE__ */ (0, Y.jsx)("h3", { children: e }),
			t,
			n && /* @__PURE__ */ (0, Y.jsx)("p", { children: n })
		]
	});
}
function Eu({ title: e, data: t, series: n, suffix: r = "" }) {
	if (!t.length || !n.length || !t.some((e) => n.some((t) => Z(e[t.id]) > 0))) return /* @__PURE__ */ (0, Y.jsx)($, {
		title: e,
		children: /* @__PURE__ */ (0, Y.jsx)(Tu, {
			title: "Пока нет данных",
			detail: "График заполнится после появления событий в выбранном периоде."
		})
	});
	let i = Math.max(1, ...n.flatMap((e) => e.values)), a = (e) => t.map((n, r) => [18 + r * 684 / Math.max(1, t.length - 1), 170 - Z(n[e.id]) / i * 138]);
	return /* @__PURE__ */ (0, Y.jsxs)($, {
		title: e,
		children: [
			/* @__PURE__ */ (0, Y.jsxs)("svg", {
				viewBox: "0 0 720 210",
				role: "img",
				"aria-label": e + ". Максимум " + Q(i) + r,
				children: [/* @__PURE__ */ (0, Y.jsx)("line", {
					x1: 18,
					x2: 702,
					y1: 170,
					y2: 170,
					stroke: "rgba(206,218,214,.22)"
				}), n.map((e) => {
					let n = a(e);
					if (!n.length) return null;
					let i = n.map(([e, t], n) => (n ? "L" : "M") + e + " " + t).join(" "), o = i + " L " + n[n.length - 1][0] + " 170 L " + n[0][0] + " 170 Z";
					return /* @__PURE__ */ (0, Y.jsxs)("g", { children: [
						/* @__PURE__ */ (0, Y.jsx)(_u.path, {
							d: o,
							fill: "url(#area-" + e.id + ")",
							initial: { opacity: 0 },
							animate: { opacity: 1 },
							transition: { duration: .65 }
						}),
						/* @__PURE__ */ (0, Y.jsx)(_u.path, {
							d: i,
							fill: "none",
							stroke: e.color,
							strokeWidth: "2.5",
							strokeLinecap: "round",
							strokeLinejoin: "round",
							initial: { pathLength: 0 },
							animate: { pathLength: 1 },
							transition: {
								duration: .9,
								ease: "easeOut"
							}
						}),
						n.filter((e, r) => t.length < 14 || r === 0 || r === n.length - 1 || r % Math.ceil(t.length / 8) === 0).map(([n, i], a) => {
							let o = t.length < 14 ? a : Math.min(t.length - 1, Math.round(n * (t.length - 1) / 720));
							return /* @__PURE__ */ (0, Y.jsx)("circle", {
								cx: n,
								cy: i,
								r: "3.5",
								fill: "#17201e",
								stroke: e.color,
								strokeWidth: "2",
								children: /* @__PURE__ */ (0, Y.jsxs)("title", { children: [
									t[o]?.label,
									": ",
									Q(Z(t[o]?.[e.id])),
									r
								] })
							}, a);
						}),
						/* @__PURE__ */ (0, Y.jsx)("defs", { children: /* @__PURE__ */ (0, Y.jsxs)("linearGradient", {
							id: "area-" + e.id,
							x1: "0",
							x2: "0",
							y1: "0",
							y2: "1",
							children: [/* @__PURE__ */ (0, Y.jsx)("stop", {
								offset: "0%",
								stopColor: e.color,
								stopOpacity: ".28"
							}), /* @__PURE__ */ (0, Y.jsx)("stop", {
								offset: "100%",
								stopColor: e.color,
								stopOpacity: "0"
							})]
						}) })
					] }, e.id);
				})]
			}),
			/* @__PURE__ */ (0, Y.jsxs)("div", {
				className: "cm-react-chart-axis",
				children: [/* @__PURE__ */ (0, Y.jsx)("span", { children: t[0].label }), /* @__PURE__ */ (0, Y.jsx)("span", { children: t[t.length - 1].label })]
			}),
			/* @__PURE__ */ (0, Y.jsx)("div", {
				className: "cm-react-chart-legend",
				children: n.map((e) => /* @__PURE__ */ (0, Y.jsxs)("div", {
					className: "cm-react-chart-legend-row",
					children: [/* @__PURE__ */ (0, Y.jsxs)("span", { children: [/* @__PURE__ */ (0, Y.jsx)("i", { style: { background: e.color } }), e.label] }), /* @__PURE__ */ (0, Y.jsxs)("strong", { children: [Q(e.values.reduce((e, t) => e + t, 0)), r] })]
				}, e.id))
			})
		]
	});
}
function Du({ title: e, rows: t, suffix: n = "" }) {
	let r = Math.max(1, ...t.map((e) => e.value));
	return !t.length || !t.some((e) => e.value > 0) ? /* @__PURE__ */ (0, Y.jsx)($, {
		title: e,
		children: /* @__PURE__ */ (0, Y.jsx)(Tu, {
			title: "Пока пусто",
			detail: "Здесь появятся значения, как только накопятся данные."
		})
	}) : /* @__PURE__ */ (0, Y.jsx)($, {
		title: e,
		children: /* @__PURE__ */ (0, Y.jsx)("div", {
			className: "cm-react-bars",
			role: "img",
			"aria-label": e,
			children: t.map((e, t) => /* @__PURE__ */ (0, Y.jsxs)("div", {
				className: "cm-react-bar-row",
				children: [
					/* @__PURE__ */ (0, Y.jsx)("span", {
						title: e.label,
						children: e.label
					}),
					/* @__PURE__ */ (0, Y.jsx)("div", {
						className: "cm-react-bar-track",
						children: /* @__PURE__ */ (0, Y.jsx)(_u.i, {
							initial: { scaleX: 0 },
							animate: { scaleX: e.value / r },
							transition: {
								duration: .55,
								delay: t * .045,
								ease: [
									.22,
									1,
									.36,
									1
								]
							},
							style: {
								transformOrigin: "left",
								background: X[t % X.length]
							}
						})
					}),
					/* @__PURE__ */ (0, Y.jsxs)("strong", { children: [Q(e.value), n] })
				]
			}, e.label))
		})
	});
}
function Ou({ title: e, rows: t }) {
	let n = t.reduce((e, t) => e + t.value, 0);
	if (!n) return /* @__PURE__ */ (0, Y.jsx)($, {
		title: e,
		children: /* @__PURE__ */ (0, Y.jsx)(Tu, {
			title: "Нет распределения",
			detail: "Разбивка появится вместе с первыми посещениями."
		})
	});
	let r = 2 * Math.PI * 43, i = 0;
	return /* @__PURE__ */ (0, Y.jsx)($, {
		title: e,
		children: /* @__PURE__ */ (0, Y.jsxs)("div", {
			className: "cm-react-donut",
			children: [/* @__PURE__ */ (0, Y.jsxs)("svg", {
				viewBox: "0 0 120 120",
				role: "img",
				"aria-label": e + ". " + Q(n) + " всего",
				children: [
					/* @__PURE__ */ (0, Y.jsx)("circle", {
						cx: "60",
						cy: "60",
						r: "43",
						fill: "none",
						stroke: "rgba(206,218,214,.12)",
						strokeWidth: "13"
					}),
					t.map((e, t) => {
						let a = r * e.value / n, o = i;
						return i += a, /* @__PURE__ */ (0, Y.jsx)(_u.circle, {
							cx: "60",
							cy: "60",
							r: "43",
							fill: "none",
							stroke: X[t % X.length],
							strokeWidth: "13",
							strokeDasharray: a + " " + (r - a),
							strokeDashoffset: -o,
							transform: "rotate(-90 60 60)",
							initial: { opacity: 0 },
							animate: { opacity: 1 },
							transition: {
								duration: .35,
								delay: t * .06
							},
							children: /* @__PURE__ */ (0, Y.jsxs)("title", { children: [
								e.label,
								": ",
								Q(e.value)
							] })
						}, e.label);
					}),
					/* @__PURE__ */ (0, Y.jsx)("text", {
						x: "60",
						y: "58",
						textAnchor: "middle",
						className: "cm-react-donut-total",
						children: Q(n)
					}),
					/* @__PURE__ */ (0, Y.jsx)("text", {
						x: "60",
						y: "73",
						textAnchor: "middle",
						className: "cm-react-donut-caption",
						children: "просмотров"
					})
				]
			}), /* @__PURE__ */ (0, Y.jsx)("div", {
				className: "cm-react-pie-legend",
				children: t.map((e, t) => /* @__PURE__ */ (0, Y.jsxs)("div", {
					className: "cm-react-chart-legend-row",
					children: [/* @__PURE__ */ (0, Y.jsxs)("span", { children: [/* @__PURE__ */ (0, Y.jsx)("i", { style: { background: X[t % X.length] } }), e.label] }), /* @__PURE__ */ (0, Y.jsxs)("strong", { children: [Math.round(e.value / n * 100), "%"] })]
				}, e.label))
			})]
		})
	});
}
function ku({ title: e, data: t }) {
	let n = /* @__PURE__ */ new Map();
	for (let e of t.events || []) {
		if (e.type !== "page_view") continue;
		let t = xu(e.day);
		t && n.set(t, (n.get(t) || 0) + Z(e.count));
	}
	if (!n.size) return /* @__PURE__ */ (0, Y.jsx)($, {
		title: e,
		children: /* @__PURE__ */ (0, Y.jsx)(Tu, {
			title: "Пока нет активности",
			detail: "Дни с просмотрами появятся после сбора первых событий."
		})
	});
	let r = /* @__PURE__ */ new Date();
	r.setUTCHours(0, 0, 0, 0);
	let i = /* @__PURE__ */ new Date(r.getTime() - 7776e6), a = Math.max(1, ...n.values()), o = Array.from({ length: 91 }, (e, t) => {
		let r = new Date(i.getTime() + t * 864e5).toISOString().slice(0, 10), o = n.get(r) || 0;
		return {
			key: r,
			value: o,
			level: o === 0 ? 0 : Math.min(4, Math.ceil(o / a * 4))
		};
	});
	return /* @__PURE__ */ (0, Y.jsx)($, {
		title: e,
		detail: "Просмотры за последние 13 недель.",
		children: /* @__PURE__ */ (0, Y.jsx)("div", {
			className: "cm-react-heatmap",
			role: "img",
			"aria-label": e,
			children: o.map((e) => /* @__PURE__ */ (0, Y.jsx)("span", {
				className: "cm-react-heatmap-cell",
				"data-level": e.level,
				tabIndex: 0,
				"aria-label": e.key + ": " + Q(e.value),
				title: e.key + ": " + Q(e.value)
			}, e.key))
		})
	});
}
function Au({ title: e, data: t }) {
	let n = wu(t).slice(0, 4), r = [...new Set(n.flatMap((e) => e.pages.map((e) => e.page)))].slice(0, 5), i = n.flatMap((e) => e.pages.filter((e) => r.includes(e.page)).map((t) => ({
		event: e.label,
		page: t.page,
		value: t.value
	}))), a = Math.max(1, ...i.map((e) => e.value));
	return i.length ? /* @__PURE__ */ (0, Y.jsx)($, {
		title: e,
		detail: "Количество событий каждого типа на публичных страницах. Это распределение, не путь отдельного пользователя.",
		children: /* @__PURE__ */ (0, Y.jsxs)("svg", {
			className: "cm-react-sankey",
			viewBox: "0 0 620 240",
			role: "img",
			"aria-label": e,
			children: [
				i.map((e, t) => {
					let i = n.findIndex((t) => t.label === e.event), o = r.indexOf(e.page), s = 28 + i * 46, c = 26 + o * 41, l = Math.max(2, e.value / a * 28);
					return /* @__PURE__ */ (0, Y.jsx)("path", {
						d: "M 165 " + s + " C 290 " + s + ", 330 " + c + ", 455 " + c,
						strokeWidth: l,
						children: /* @__PURE__ */ (0, Y.jsxs)("title", { children: [
							e.event,
							" → ",
							yu[e.page] || e.page,
							": ",
							Q(e.value)
						] })
					}, t);
				}),
				n.map((e, t) => /* @__PURE__ */ (0, Y.jsx)("text", {
					x: "12",
					y: 32 + t * 46,
					children: e.label
				}, e.event)),
				r.map((e, t) => /* @__PURE__ */ (0, Y.jsx)("text", {
					x: "470",
					y: 30 + t * 41,
					children: yu[e] || e
				}, e))
			]
		})
	}) : /* @__PURE__ */ (0, Y.jsx)($, {
		title: e,
		children: /* @__PURE__ */ (0, Y.jsx)(Tu, {
			title: "Нет событий",
			detail: "Поток появится после первых посещений и действий на сайте."
		})
	});
}
function ju({ samples: e }) {
	let t = [
		"players",
		"online",
		"tps",
		"mspt"
	].find((t) => e.some((e) => Number.isFinite(Number(e[t])))) || "", n = e.slice(-60).map((e) => ({
		label: xu(e.at) || String(e.at || ""),
		value: Z(t ? e[t] : 0)
	})).filter((e) => e.label);
	return !t || !n.length ? /* @__PURE__ */ (0, Y.jsx)($, {
		title: "Снимки сервера",
		children: /* @__PURE__ */ (0, Y.jsx)(Tu, {
			title: "Метрики сервера не подключены",
			detail: "Показатель появится после подключения источника мониторинга игрового сервера."
		})
	}) : /* @__PURE__ */ (0, Y.jsx)(Eu, {
		title: "Снимки сервера · " + t.toUpperCase(),
		data: n,
		series: [{
			id: "value",
			label: t.toUpperCase(),
			color: X[0],
			values: n.map((e) => e.value)
		}]
	});
}
function Mu({ title: e, regions: t }) {
	let n = t || [], r = [], i = (e) => {
		Array.isArray(e) && (e.length >= 2 && typeof e[0] == "number" && typeof e[1] == "number" ? r.push([e[0], e[1]]) : e.forEach(i));
	};
	if (n.forEach((e) => i(e.geometry?.coordinates)), !r.length) return /* @__PURE__ */ (0, Y.jsx)($, {
		title: e,
		children: /* @__PURE__ */ (0, Y.jsx)(Tu, {
			title: "Карта регионов недоступна",
			detail: "В источнике пока нет координатных данных для карты."
		})
	});
	let a = Math.min(...r.map((e) => e[0])), o = Math.max(...r.map((e) => e[0])), s = Math.min(...r.map((e) => e[1])), c = Math.max(...r.map((e) => e[1])), l = (e) => 10 + (e - a) / Math.max(1e-4, o - a) * 540, u = (e) => 190 - (e - s) / Math.max(1e-4, c - s) * 170, d = (e) => !Array.isArray(e) || !e.length ? "" : Array.isArray(e[0]) && typeof e[0][0] == "number" ? e.map((e, t) => (t ? "L" : "M") + l(e[0]) + " " + u(e[1])).join(" ") + " Z" : e.map(d).join(" "), f = Math.max(1, ...n.map((e) => Z(e.properties?.value)));
	return /* @__PURE__ */ (0, Y.jsx)($, {
		title: e,
		children: /* @__PURE__ */ (0, Y.jsx)("svg", {
			viewBox: "0 0 560 210",
			role: "img",
			"aria-label": e,
			children: n.map((e, t) => {
				let n = Z(e.properties?.value), r = Math.min(1, n / f);
				return /* @__PURE__ */ (0, Y.jsx)("path", {
					className: "cm-react-map-feature",
					d: d(e.geometry?.coordinates),
					fill: "hsl(152 56% " + (18 + r * 34) + "%)",
					children: /* @__PURE__ */ (0, Y.jsxs)("title", { children: [
						String(e.properties?.name || "Регион"),
						": ",
						Q(n)
					] })
				}, String(e.properties?.id || t));
			})
		})
	});
}
function Nu({ model: e }) {
	if (e.sub === "live") return /* @__PURE__ */ (0, Y.jsx)("div", {
		className: "cm-react-chart-section",
		children: /* @__PURE__ */ (0, Y.jsx)(ju, { samples: e.data?.live?.samples || [] })
	});
	let t = e.data || {}, n = Su(t), r = Cu(t), i = wu(t), a = e.sub === "economy", o = (t.days || []).map((e) => ({
		label: xu(e.at),
		AR: Z(e.AR),
		CM: Z(e.CM),
		value: Z(e.AR) + Z(e.CM)
	})).filter((e) => e.label).slice(-90), s = [{
		label: "AR",
		value: Z(t.revenue?.AR)
	}, {
		label: "CM",
		value: Z(t.revenue?.CM)
	}].filter((e) => e.value > 0), c = (t.top || []).slice(0, 8).map((e) => ({
		label: e.name || e.id || "Предмет",
		value: Z(e.qty)
	})), l = a ? /* @__PURE__ */ (0, Y.jsxs)(Y.Fragment, { children: [
		/* @__PURE__ */ (0, Y.jsx)(Eu, {
			title: "Оборот по дням",
			data: o,
			series: [{
				id: "AR",
				label: "AR",
				color: X[0],
				values: o.map((e) => e.AR)
			}, {
				id: "CM",
				label: "CM",
				color: X[2],
				values: o.map((e) => e.CM)
			}]
		}),
		/* @__PURE__ */ (0, Y.jsx)(Ou, {
			title: "Оборот по валюте",
			rows: s
		}),
		/* @__PURE__ */ (0, Y.jsx)(Du, {
			title: "Выданные предметы",
			rows: c,
			suffix: " шт."
		})
	] }) : e.sub === "events" ? /* @__PURE__ */ (0, Y.jsxs)(Y.Fragment, { children: [/* @__PURE__ */ (0, Y.jsx)(ku, {
		title: "Активность сайта",
		data: t
	}), /* @__PURE__ */ (0, Y.jsx)(Au, {
		title: "События по страницам",
		data: t
	})] }) : e.sub === "pages" ? /* @__PURE__ */ (0, Y.jsxs)(Y.Fragment, { children: [/* @__PURE__ */ (0, Y.jsx)(Eu, {
		title: "Просмотры по дням",
		data: n,
		series: [{
			id: "value",
			label: "Просмотры",
			color: X[0],
			values: n.map((e) => e.value)
		}]
	}), /* @__PURE__ */ (0, Y.jsx)(Ou, {
		title: "Доля страниц",
		rows: r
	})] }) : /* @__PURE__ */ (0, Y.jsxs)(Y.Fragment, { children: [
		/* @__PURE__ */ (0, Y.jsx)(Eu, {
			title: "Просмотры по дням",
			data: n,
			series: [{
				id: "value",
				label: "Просмотры",
				color: X[0],
				values: n.map((e) => e.value)
			}]
		}),
		/* @__PURE__ */ (0, Y.jsx)(Du, {
			title: "Популярные страницы",
			rows: r
		}),
		/* @__PURE__ */ (0, Y.jsx)(ku, {
			title: "Активность за 13 недель",
			data: t
		}),
		/* @__PURE__ */ (0, Y.jsx)(Ou, {
			title: "Доля страниц",
			rows: r
		}),
		/* @__PURE__ */ (0, Y.jsx)(Au, {
			title: "Действия по страницам",
			data: t
		})
	] }), u = !!t.regions?.features?.length, d = t.live?.samples || [];
	return /* @__PURE__ */ (0, Y.jsxs)("section", {
		className: "cm-react-chart-section",
		"aria-label": "Графики CopiMine",
		children: [
			/* @__PURE__ */ (0, Y.jsx)("div", {
				className: "cm-react-chart-grid",
				children: l
			}),
			(u || d.length > 0) && /* @__PURE__ */ (0, Y.jsxs)("div", {
				className: "cm-react-chart-grid",
				children: [u && /* @__PURE__ */ (0, Y.jsx)(Mu, {
					title: "Активность по регионам",
					regions: t.regions?.features
				}), d.length > 0 && /* @__PURE__ */ (0, Y.jsx)(ju, { samples: d })]
			}),
			i.length === 0 && e.sub !== "economy" && r.length === 0 && n.length === 0 ? /* @__PURE__ */ (0, Y.jsx)("p", {
				className: "cm-react-no-chart-data",
				children: "Данные появятся после первых посещений сайта."
			}) : null
		]
	});
}
export { Nu as AdminAnalyticsCharts };
