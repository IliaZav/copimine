import { i as e, n as t, r as n, t as r } from "./jsx-runtime-DaXKb_ub.mjs";
// node_modules/scheduler/cjs/scheduler.production.js
var i = /* @__PURE__ */ n(((e) => {
	function t(e, t) {
		var n = e.length;
		e.push(t);
		a: for (; 0 < n;) {
			var r = n - 1 >>> 1, a = e[r];
			if (0 < i(a, t)) e[r] = t, e[n] = a, n = r;
			else break a;
		}
	}
	function n(e) {
		return e.length === 0 ? null : e[0];
	}
	function r(e) {
		if (e.length === 0) return null;
		var t = e[0], n = e.pop();
		if (n !== t) {
			e[0] = n;
			a: for (var r = 0, a = e.length, o = a >>> 1; r < o;) {
				var s = 2 * (r + 1) - 1, c = e[s], l = s + 1, u = e[l];
				if (0 > i(c, n)) l < a && 0 > i(u, c) ? (e[r] = u, e[l] = n, r = l) : (e[r] = c, e[s] = n, r = s);
				else if (l < a && 0 > i(u, n)) e[r] = u, e[l] = n, r = l;
				else break a;
			}
		}
		return t;
	}
	function i(e, t) {
		var n = e.sortIndex - t.sortIndex;
		return n === 0 ? e.id - t.id : n;
	}
	if (e.unstable_now = void 0, typeof performance == "object" && typeof performance.now == "function") {
		var a = performance;
		e.unstable_now = function() {
			return a.now();
		};
	} else {
		var o = Date, s = o.now();
		e.unstable_now = function() {
			return o.now() - s;
		};
	}
	var c = [], l = [], u = 1, d = null, f = 3, p = !1, m = !1, h = !1, g = !1, _ = typeof setTimeout == "function" ? setTimeout : null, v = typeof clearTimeout == "function" ? clearTimeout : null, y = typeof setImmediate < "u" ? setImmediate : null;
	function b(e) {
		for (var i = n(l); i !== null;) {
			if (i.callback === null) r(l);
			else if (i.startTime <= e) r(l), i.sortIndex = i.expirationTime, t(c, i);
			else break;
			i = n(l);
		}
	}
	function ee(e) {
		if (h = !1, b(e), !m) {
			if (n(c) !== null) m = !0, te || (te = !0, oe());
			else {
				var t = n(l);
				t !== null && le(ee, t.startTime - e);
			}
		}
	}
	var te = !1, ne = -1, re = 5, ie = -1;
	function ae() {
		return g ? !0 : !(e.unstable_now() - ie < re);
	}
	function x() {
		if (g = !1, te) {
			var t = e.unstable_now();
			ie = t;
			var i = !0;
			try {
				a: {
					m = !1, h && (h = !1, v(ne), ne = -1), p = !0;
					var a = f;
					try {
						b: {
							for (b(t), d = n(c); d !== null && !(d.expirationTime > t && ae());) {
								var o = d.callback;
								if (typeof o == "function") {
									d.callback = null, f = d.priorityLevel;
									var s = o(d.expirationTime <= t);
									if (t = e.unstable_now(), typeof s == "function") {
										d.callback = s, b(t), i = !0;
										break b;
									}
									d === n(c) && r(c), b(t);
								} else r(c);
								d = n(c);
							}
							if (d !== null) i = !0;
							else {
								var u = n(l);
								u !== null && le(ee, u.startTime - t), i = !1;
							}
						}
						break a;
					} finally {
						d = null, f = a, p = !1;
					}
					i = void 0;
				}
			} finally {
				i ? oe() : te = !1;
			}
		}
	}
	var oe;
	if (typeof y == "function") oe = function() {
		y(x);
	};
	else if (typeof MessageChannel < "u") {
		var se = new MessageChannel(), ce = se.port2;
		se.port1.onmessage = x, oe = function() {
			ce.postMessage(null);
		};
	} else oe = function() {
		_(x, 0);
	};
	function le(t, n) {
		ne = _(function() {
			t(e.unstable_now());
		}, n);
	}
	e.unstable_IdlePriority = 5, e.unstable_ImmediatePriority = 1, e.unstable_LowPriority = 4, e.unstable_NormalPriority = 3, e.unstable_Profiling = null, e.unstable_UserBlockingPriority = 2, e.unstable_cancelCallback = function(e) {
		e.callback = null;
	}, e.unstable_forceFrameRate = function(e) {
		0 > e || 125 < e ? console.error("forceFrameRate takes a positive int between 0 and 125, forcing frame rates higher than 125 fps is not supported") : re = 0 < e ? Math.floor(1e3 / e) : 5;
	}, e.unstable_getCurrentPriorityLevel = function() {
		return f;
	}, e.unstable_next = function(e) {
		switch (f) {
			case 1:
			case 2:
			case 3:
				var t = 3;
				break;
			default: t = f;
		}
		var n = f;
		f = t;
		try {
			return e();
		} finally {
			f = n;
		}
	}, e.unstable_requestPaint = function() {
		g = !0;
	}, e.unstable_runWithPriority = function(e, t) {
		switch (e) {
			case 1:
			case 2:
			case 3:
			case 4:
			case 5: break;
			default: e = 3;
		}
		var n = f;
		f = e;
		try {
			return t();
		} finally {
			f = n;
		}
	}, e.unstable_scheduleCallback = function(r, i, a) {
		var o = e.unstable_now();
		switch (typeof a == "object" && a ? (a = a.delay, a = typeof a == "number" && 0 < a ? o + a : o) : a = o, r) {
			case 1:
				var s = -1;
				break;
			case 2:
				s = 250;
				break;
			case 5:
				s = 1073741823;
				break;
			case 4:
				s = 1e4;
				break;
			default: s = 5e3;
		}
		return s = a + s, r = {
			id: u++,
			callback: i,
			priorityLevel: r,
			startTime: a,
			expirationTime: s,
			sortIndex: -1
		}, a > o ? (r.sortIndex = a, t(l, r), n(c) === null && r === n(l) && (h ? (v(ne), ne = -1) : h = !0, le(ee, a - o))) : (r.sortIndex = s, t(c, r), m || p || (m = !0, te || (te = !0, oe()))), r;
	}, e.unstable_shouldYield = ae, e.unstable_wrapCallback = function(e) {
		var t = f;
		return function() {
			var n = f;
			f = t;
			try {
				return e.apply(this, arguments);
			} finally {
				f = n;
			}
		};
	};
})), a = /* @__PURE__ */ n(((e) => {
	process.env.NODE_ENV !== "production" && (function() {
		function t() {
			if (ee = !1, ie) {
				var t = e.unstable_now();
				oe = t;
				var n = !0;
				try {
					a: {
						y = !1, b && (b = !1, ne(ae), ae = -1), v = !0;
						var a = _;
						try {
							b: {
								for (o(t), g = r(p); g !== null && !(g.expirationTime > t && c());) {
									var u = g.callback;
									if (typeof u == "function") {
										g.callback = null, _ = g.priorityLevel;
										var d = u(g.expirationTime <= t);
										if (t = e.unstable_now(), typeof d == "function") {
											g.callback = d, o(t), n = !0;
											break b;
										}
										g === r(p) && i(p), o(t);
									} else i(p);
									g = r(p);
								}
								if (g !== null) n = !0;
								else {
									var f = r(m);
									f !== null && l(s, f.startTime - t), n = !1;
								}
							}
							break a;
						} finally {
							g = null, _ = a, v = !1;
						}
						n = void 0;
					}
				} finally {
					n ? se() : ie = !1;
				}
			}
		}
		function n(e, t) {
			var n = e.length;
			e.push(t);
			a: for (; 0 < n;) {
				var r = n - 1 >>> 1, i = e[r];
				if (0 < a(i, t)) e[r] = t, e[n] = i, n = r;
				else break a;
			}
		}
		function r(e) {
			return e.length === 0 ? null : e[0];
		}
		function i(e) {
			if (e.length === 0) return null;
			var t = e[0], n = e.pop();
			if (n !== t) {
				e[0] = n;
				a: for (var r = 0, i = e.length, o = i >>> 1; r < o;) {
					var s = 2 * (r + 1) - 1, c = e[s], l = s + 1, u = e[l];
					if (0 > a(c, n)) l < i && 0 > a(u, c) ? (e[r] = u, e[l] = n, r = l) : (e[r] = c, e[s] = n, r = s);
					else if (l < i && 0 > a(u, n)) e[r] = u, e[l] = n, r = l;
					else break a;
				}
			}
			return t;
		}
		function a(e, t) {
			var n = e.sortIndex - t.sortIndex;
			return n === 0 ? e.id - t.id : n;
		}
		function o(e) {
			for (var t = r(m); t !== null;) {
				if (t.callback === null) i(m);
				else if (t.startTime <= e) i(m), t.sortIndex = t.expirationTime, n(p, t);
				else break;
				t = r(m);
			}
		}
		function s(e) {
			if (b = !1, o(e), !y) {
				if (r(p) !== null) y = !0, ie || (ie = !0, se());
				else {
					var t = r(m);
					t !== null && l(s, t.startTime - e);
				}
			}
		}
		function c() {
			return ee ? !0 : !(e.unstable_now() - oe < x);
		}
		function l(t, n) {
			ae = te(function() {
				t(e.unstable_now());
			}, n);
		}
		if (typeof __REACT_DEVTOOLS_GLOBAL_HOOK__ < "u" && typeof __REACT_DEVTOOLS_GLOBAL_HOOK__.registerInternalModuleStart == "function" && __REACT_DEVTOOLS_GLOBAL_HOOK__.registerInternalModuleStart(Error()), e.unstable_now = void 0, typeof performance == "object" && typeof performance.now == "function") {
			var u = performance;
			e.unstable_now = function() {
				return u.now();
			};
		} else {
			var d = Date, f = d.now();
			e.unstable_now = function() {
				return d.now() - f;
			};
		}
		var p = [], m = [], h = 1, g = null, _ = 3, v = !1, y = !1, b = !1, ee = !1, te = typeof setTimeout == "function" ? setTimeout : null, ne = typeof clearTimeout == "function" ? clearTimeout : null, re = typeof setImmediate < "u" ? setImmediate : null, ie = !1, ae = -1, x = 5, oe = -1;
		if (typeof re == "function") var se = function() {
			re(t);
		};
		else if (typeof MessageChannel < "u") {
			var ce = new MessageChannel(), le = ce.port2;
			ce.port1.onmessage = t, se = function() {
				le.postMessage(null);
			};
		} else se = function() {
			te(t, 0);
		};
		e.unstable_IdlePriority = 5, e.unstable_ImmediatePriority = 1, e.unstable_LowPriority = 4, e.unstable_NormalPriority = 3, e.unstable_Profiling = null, e.unstable_UserBlockingPriority = 2, e.unstable_cancelCallback = function(e) {
			e.callback = null;
		}, e.unstable_forceFrameRate = function(e) {
			0 > e || 125 < e ? console.error("forceFrameRate takes a positive int between 0 and 125, forcing frame rates higher than 125 fps is not supported") : x = 0 < e ? Math.floor(1e3 / e) : 5;
		}, e.unstable_getCurrentPriorityLevel = function() {
			return _;
		}, e.unstable_next = function(e) {
			switch (_) {
				case 1:
				case 2:
				case 3:
					var t = 3;
					break;
				default: t = _;
			}
			var n = _;
			_ = t;
			try {
				return e();
			} finally {
				_ = n;
			}
		}, e.unstable_requestPaint = function() {
			ee = !0;
		}, e.unstable_runWithPriority = function(e, t) {
			switch (e) {
				case 1:
				case 2:
				case 3:
				case 4:
				case 5: break;
				default: e = 3;
			}
			var n = _;
			_ = e;
			try {
				return t();
			} finally {
				_ = n;
			}
		}, e.unstable_scheduleCallback = function(t, i, a) {
			var o = e.unstable_now();
			switch (typeof a == "object" && a ? (a = a.delay, a = typeof a == "number" && 0 < a ? o + a : o) : a = o, t) {
				case 1:
					var c = -1;
					break;
				case 2:
					c = 250;
					break;
				case 5:
					c = 1073741823;
					break;
				case 4:
					c = 1e4;
					break;
				default: c = 5e3;
			}
			return c = a + c, t = {
				id: h++,
				callback: i,
				priorityLevel: t,
				startTime: a,
				expirationTime: c,
				sortIndex: -1
			}, a > o ? (t.sortIndex = a, n(m, t), r(p) === null && t === r(m) && (b ? (ne(ae), ae = -1) : b = !0, l(s, a - o))) : (t.sortIndex = c, n(p, t), y || v || (y = !0, ie || (ie = !0, se()))), t;
		}, e.unstable_shouldYield = c, e.unstable_wrapCallback = function(e) {
			var t = _;
			return function() {
				var n = _;
				_ = t;
				try {
					return e.apply(this, arguments);
				} finally {
					_ = n;
				}
			};
		}, typeof __REACT_DEVTOOLS_GLOBAL_HOOK__ < "u" && typeof __REACT_DEVTOOLS_GLOBAL_HOOK__.registerInternalModuleStop == "function" && __REACT_DEVTOOLS_GLOBAL_HOOK__.registerInternalModuleStop(Error());
	})();
})), o = /* @__PURE__ */ n(((e, t) => {
	t.exports = process.env.NODE_ENV === "production" ? i() : a();
})), s = /* @__PURE__ */ n(((e) => {
	var n = t();
	function r(e) {
		var t = "https://react.dev/errors/" + e;
		if (1 < arguments.length) {
			t += "?args[]=" + encodeURIComponent(arguments[1]);
			for (var n = 2; n < arguments.length; n++) t += "&args[]=" + encodeURIComponent(arguments[n]);
		}
		return "Minified React error #" + e + "; visit " + t + " for the full message or use the non-minified dev environment for full errors and additional helpful warnings.";
	}
	function i() {}
	var a = {
		d: {
			f: i,
			r: function() {
				throw Error(r(522));
			},
			D: i,
			C: i,
			L: i,
			m: i,
			X: i,
			S: i,
			M: i
		},
		p: 0,
		findDOMNode: null
	}, o = Symbol.for("react.portal"), s = Symbol.for("react.recoverable"), c = Symbol.for("react.optimistic_key");
	function l(e, t, n) {
		var r = 3 < arguments.length && arguments[3] !== void 0 ? arguments[3] : null;
		return {
			$$typeof: o,
			key: r == null ? null : r === c ? c : "" + r,
			children: e,
			containerInfo: t,
			implementation: n
		};
	}
	var u = n.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE;
	function d(e, t) {
		if (e === "font") return "";
		if (typeof t == "string") return t === "use-credentials" ? t : "";
	}
	e.__DOM_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE = a, e.browser = function(e) {
		return {
			$$typeof: s,
			_reason: e
		};
	}, e.createPortal = function(e, t) {
		var n = 2 < arguments.length && arguments[2] !== void 0 ? arguments[2] : null;
		if (!t || t.nodeType !== 1 && t.nodeType !== 9 && t.nodeType !== 11) throw Error(r(299));
		return l(e, t, null, n);
	}, e.flushSync = function(e) {
		var t = u.T, n = a.p;
		try {
			if (u.T = null, a.p = 2, e) return e();
		} finally {
			u.T = t, a.p = n, a.d.f();
		}
	}, e.preconnect = function(e, t) {
		typeof e == "string" && (t ? (t = t.crossOrigin, t = typeof t == "string" ? t === "use-credentials" ? t : "" : void 0) : t = null, a.d.C(e, t));
	}, e.prefetchDNS = function(e) {
		typeof e == "string" && a.d.D(e);
	}, e.preinit = function(e, t) {
		if (typeof e == "string" && t && typeof t.as == "string") {
			var n = t.as, r = d(n, t.crossOrigin), i = typeof t.integrity == "string" ? t.integrity : void 0, o = typeof t.fetchPriority == "string" ? t.fetchPriority : void 0;
			n === "style" ? a.d.S(e, typeof t.precedence == "string" ? t.precedence : void 0, {
				crossOrigin: r,
				integrity: i,
				fetchPriority: o
			}) : n === "script" && a.d.X(e, {
				crossOrigin: r,
				integrity: i,
				fetchPriority: o,
				nonce: typeof t.nonce == "string" ? t.nonce : void 0
			});
		}
	}, e.preinitModule = function(e, t) {
		if (typeof e == "string") {
			if (typeof t == "object" && t) {
				if (t.as == null || t.as === "script") {
					var n = d(t.as, t.crossOrigin);
					a.d.M(e, {
						crossOrigin: n,
						integrity: typeof t.integrity == "string" ? t.integrity : void 0,
						nonce: typeof t.nonce == "string" ? t.nonce : void 0,
						fetchPriority: typeof t.fetchPriority == "string" ? t.fetchPriority : void 0
					});
				}
			} else t ?? a.d.M(e);
		}
	}, e.preload = function(e, t) {
		if (typeof e == "string" && typeof t == "object" && t && typeof t.as == "string") {
			var n = t.as, r = d(n, t.crossOrigin);
			a.d.L(e, n, {
				crossOrigin: r,
				integrity: typeof t.integrity == "string" ? t.integrity : void 0,
				nonce: typeof t.nonce == "string" ? t.nonce : void 0,
				type: typeof t.type == "string" ? t.type : void 0,
				fetchPriority: typeof t.fetchPriority == "string" ? t.fetchPriority : void 0,
				referrerPolicy: typeof t.referrerPolicy == "string" ? t.referrerPolicy : void 0,
				imageSrcSet: typeof t.imageSrcSet == "string" ? t.imageSrcSet : void 0,
				imageSizes: typeof t.imageSizes == "string" ? t.imageSizes : void 0,
				media: typeof t.media == "string" ? t.media : void 0
			});
		}
	}, e.preloadModule = function(e, t) {
		if (typeof e == "string") {
			if (t) {
				var n = d(t.as, t.crossOrigin);
				a.d.m(e, {
					as: typeof t.as == "string" && t.as !== "script" ? t.as : void 0,
					crossOrigin: n,
					integrity: typeof t.integrity == "string" ? t.integrity : void 0,
					nonce: typeof t.nonce == "string" ? t.nonce : void 0,
					fetchPriority: typeof t.fetchPriority == "string" ? t.fetchPriority : void 0
				});
			} else a.d.m(e);
		}
	}, e.requestFormReset = function(e) {
		a.d.r(e);
	}, e.unstable_batchedUpdates = function(e, t) {
		return e(t);
	}, e.useFormState = function(e, t, n) {
		return u.H.useFormState(e, t, n);
	}, e.useFormStatus = function() {
		return u.H.useHostTransitionStatus();
	}, e.version = "19.3.1";
})), c = /* @__PURE__ */ n(((e) => {
	process.env.NODE_ENV !== "production" && (function() {
		function n() {}
		function r(e) {
			return "" + e;
		}
		function i(e, t, n) {
			var i = 3 < arguments.length && arguments[3] !== void 0 ? arguments[3] : null;
			if (i == null) i = null;
			else if (i === p) i = p;
			else {
				try {
					r(i);
					var a = !1;
				} catch {
					a = !0;
				}
				a && (console.error("The provided key is an unsupported type %s. This value must be coerced to a string before using it here.", typeof Symbol == "function" && Symbol.toStringTag && i[Symbol.toStringTag] || i.constructor.name || "Object"), r(i)), i = "" + i;
			}
			return {
				$$typeof: d,
				key: i,
				children: e,
				containerInfo: t,
				implementation: n
			};
		}
		function a(e, t) {
			if (e === "font") return "";
			if (typeof t == "string") return t === "use-credentials" ? t : "";
		}
		function o(e) {
			return e === null ? "`null`" : e === void 0 ? "`undefined`" : e === "" ? "an empty string" : "something with type \"" + typeof e + "\"";
		}
		function s(e) {
			return e === null ? "`null`" : e === void 0 ? "`undefined`" : e === "" ? "an empty string" : typeof e == "string" ? JSON.stringify(e) : typeof e == "number" ? "`" + e + "`" : "something with type \"" + typeof e + "\"";
		}
		function c() {
			var e = m.H;
			return e === null && console.error("Invalid hook call. Hooks can only be called inside of the body of a function component. This could happen for one of the following reasons:\n1. You might have mismatching versions of React and the renderer (such as React DOM)\n2. You might be breaking the Rules of Hooks\n3. You might have more than one copy of React in the same app\nSee https://react.dev/link/invalid-hook-call for tips about how to debug and fix this problem."), e;
		}
		typeof __REACT_DEVTOOLS_GLOBAL_HOOK__ < "u" && typeof __REACT_DEVTOOLS_GLOBAL_HOOK__.registerInternalModuleStart == "function" && __REACT_DEVTOOLS_GLOBAL_HOOK__.registerInternalModuleStart(Error());
		var l = t(), u = {
			d: {
				f: n,
				r: function() {
					throw Error("Invalid form element. requestFormReset must be passed a form that was rendered by React.");
				},
				D: n,
				C: n,
				L: n,
				m: n,
				X: n,
				S: n,
				M: n
			},
			p: 0,
			findDOMNode: null
		}, d = Symbol.for("react.portal"), f = Symbol.for("react.recoverable"), p = Symbol.for("react.optimistic_key"), m = l.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE;
		typeof Map == "function" && Map.prototype != null && typeof Map.prototype.forEach == "function" && typeof Set == "function" && Set.prototype != null && typeof Set.prototype.clear == "function" && typeof Set.prototype.forEach == "function" || console.error("React depends on Map and Set built-in types. Make sure that you load a polyfill in older browsers. https://reactjs.org/link/react-polyfills"), e.__DOM_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE = u, e.browser = function(e) {
			return {
				$$typeof: f,
				_reason: e
			};
		}, e.createPortal = function(e, t) {
			var n = 2 < arguments.length && arguments[2] !== void 0 ? arguments[2] : null;
			if (!t || t.nodeType !== 1 && t.nodeType !== 9 && t.nodeType !== 11) throw Error("Target container is not a DOM element.");
			return i(e, t, null, n);
		}, e.flushSync = function(e) {
			var t = m.T, n = u.p;
			try {
				if (m.T = null, u.p = 2, e) return e();
			} finally {
				m.T = t, u.p = n, u.d.f() && console.error("flushSync was called from inside a lifecycle method. React cannot flush when React is already rendering. Consider moving this call to a scheduler task or micro task.");
			}
		}, e.preconnect = function(e, t) {
			typeof e == "string" && e ? t != null && typeof t != "object" ? console.error("ReactDOM.preconnect(): Expected the `options` argument (second) to be an object but encountered %s instead. The only supported option at this time is `crossOrigin` which accepts a string.", s(t)) : t != null && typeof t.crossOrigin != "string" && console.error("ReactDOM.preconnect(): Expected the `crossOrigin` option (second argument) to be a string but encountered %s instead. Try removing this option or passing a string value instead.", o(t.crossOrigin)) : console.error("ReactDOM.preconnect(): Expected the `href` argument (first) to be a non-empty string but encountered %s instead.", o(e)), typeof e == "string" && (t ? (t = t.crossOrigin, t = typeof t == "string" ? t === "use-credentials" ? t : "" : void 0) : t = null, u.d.C(e, t));
		}, e.prefetchDNS = function(e) {
			if (typeof e != "string" || !e) console.error("ReactDOM.prefetchDNS(): Expected the `href` argument (first) to be a non-empty string but encountered %s instead.", o(e));
			else if (1 < arguments.length) {
				var t = arguments[1];
				typeof t == "object" && t.hasOwnProperty("crossOrigin") ? console.error("ReactDOM.prefetchDNS(): Expected only one argument, `href`, but encountered %s as a second argument instead. This argument is reserved for future options and is currently disallowed. It looks like the you are attempting to set a crossOrigin property for this DNS lookup hint. Browsers do not perform DNS queries using CORS and setting this attribute on the resource hint has no effect. Try calling ReactDOM.prefetchDNS() with just a single string argument, `href`.", s(t)) : console.error("ReactDOM.prefetchDNS(): Expected only one argument, `href`, but encountered %s as a second argument instead. This argument is reserved for future options and is currently disallowed. Try calling ReactDOM.prefetchDNS() with just a single string argument, `href`.", s(t));
			}
			typeof e == "string" && u.d.D(e);
		}, e.preinit = function(e, t) {
			if (typeof e == "string" && e ? typeof t != "object" || !t ? console.error("ReactDOM.preinit(): Expected the `options` argument (second) to be an object with an `as` property describing the type of resource to be preinitialized but encountered %s instead.", s(t)) : t.as !== "style" && t.as !== "script" && console.error("ReactDOM.preinit(): Expected the `as` property in the `options` argument (second) to contain a valid value describing the type of resource to be preinitialized but encountered %s instead. Valid values for `as` are \"style\" and \"script\".", s(t.as)) : console.error("ReactDOM.preinit(): Expected the `href` argument (first) to be a non-empty string but encountered %s instead.", o(e)), typeof e == "string" && t && typeof t.as == "string") {
				var n = t.as, r = a(n, t.crossOrigin), i = typeof t.integrity == "string" ? t.integrity : void 0, c = typeof t.fetchPriority == "string" ? t.fetchPriority : void 0;
				n === "style" ? u.d.S(e, typeof t.precedence == "string" ? t.precedence : void 0, {
					crossOrigin: r,
					integrity: i,
					fetchPriority: c
				}) : n === "script" && u.d.X(e, {
					crossOrigin: r,
					integrity: i,
					fetchPriority: c,
					nonce: typeof t.nonce == "string" ? t.nonce : void 0
				});
			}
		}, e.preinitModule = function(e, t) {
			var n = "";
			if (typeof e == "string" && e || (n += " The `href` argument encountered was " + o(e) + "."), t !== void 0 && typeof t != "object" ? n += " The `options` argument encountered was " + o(t) + "." : t && "as" in t && t.as !== "script" && (n += " The `as` option encountered was " + s(t.as) + "."), n) console.error("ReactDOM.preinitModule(): Expected up to two arguments, a non-empty `href` string and, optionally, an `options` object with a valid `as` property.%s", n);
			else switch (n = t && typeof t.as == "string" ? t.as : "script", n) {
				case "script": break;
				default: n = s(n), console.error("ReactDOM.preinitModule(): Currently the only supported \"as\" type for this function is \"script\" but received \"%s\" instead. This warning was generated for `href` \"%s\". In the future other module types will be supported, aligning with the import-attributes proposal. Learn more here: (https://github.com/tc39/proposal-import-attributes)", n, e);
			}
			typeof e == "string" && (typeof t == "object" && t ? (t.as == null || t.as === "script") && (n = a(t.as, t.crossOrigin), u.d.M(e, {
				crossOrigin: n,
				integrity: typeof t.integrity == "string" ? t.integrity : void 0,
				nonce: typeof t.nonce == "string" ? t.nonce : void 0,
				fetchPriority: typeof t.fetchPriority == "string" ? t.fetchPriority : void 0
			})) : t ?? u.d.M(e));
		}, e.preload = function(e, t) {
			var n = "";
			if (typeof e == "string" && e || (n += " The `href` argument encountered was " + o(e) + "."), typeof t != "object" || !t ? n += " The `options` argument encountered was " + o(t) + "." : typeof t.as == "string" && t.as || (n += " The `as` option encountered was " + o(t.as) + "."), n && console.error("ReactDOM.preload(): Expected two arguments, a non-empty `href` string and an `options` object with an `as` property valid for a `<link rel=\"preload\" as=\"...\" />` tag.%s", n), typeof e == "string" && typeof t == "object" && t && typeof t.as == "string") {
				n = t.as;
				var r = a(n, t.crossOrigin);
				u.d.L(e, n, {
					crossOrigin: r,
					integrity: typeof t.integrity == "string" ? t.integrity : void 0,
					nonce: typeof t.nonce == "string" ? t.nonce : void 0,
					type: typeof t.type == "string" ? t.type : void 0,
					fetchPriority: typeof t.fetchPriority == "string" ? t.fetchPriority : void 0,
					referrerPolicy: typeof t.referrerPolicy == "string" ? t.referrerPolicy : void 0,
					imageSrcSet: typeof t.imageSrcSet == "string" ? t.imageSrcSet : void 0,
					imageSizes: typeof t.imageSizes == "string" ? t.imageSizes : void 0,
					media: typeof t.media == "string" ? t.media : void 0
				});
			}
		}, e.preloadModule = function(e, t) {
			var n = "";
			typeof e == "string" && e || (n += " The `href` argument encountered was " + o(e) + "."), t !== void 0 && typeof t != "object" ? n += " The `options` argument encountered was " + o(t) + "." : t && "as" in t && typeof t.as != "string" && (n += " The `as` option encountered was " + o(t.as) + "."), n && console.error("ReactDOM.preloadModule(): Expected two arguments, a non-empty `href` string and, optionally, an `options` object with an `as` property valid for a `<link rel=\"modulepreload\" as=\"...\" />` tag.%s", n), typeof e == "string" && (t ? (n = a(t.as, t.crossOrigin), u.d.m(e, {
				as: typeof t.as == "string" && t.as !== "script" ? t.as : void 0,
				crossOrigin: n,
				integrity: typeof t.integrity == "string" ? t.integrity : void 0,
				nonce: typeof t.nonce == "string" ? t.nonce : void 0,
				fetchPriority: typeof t.fetchPriority == "string" ? t.fetchPriority : void 0
			})) : u.d.m(e));
		}, e.requestFormReset = function(e) {
			u.d.r(e);
		}, e.unstable_batchedUpdates = function(e, t) {
			return e(t);
		}, e.useFormState = function(e, t, n) {
			return c().useFormState(e, t, n);
		}, e.useFormStatus = function() {
			return c().useHostTransitionStatus();
		}, e.version = "19.3.1", typeof __REACT_DEVTOOLS_GLOBAL_HOOK__ < "u" && typeof __REACT_DEVTOOLS_GLOBAL_HOOK__.registerInternalModuleStop == "function" && __REACT_DEVTOOLS_GLOBAL_HOOK__.registerInternalModuleStop(Error());
	})();
})), l = /* @__PURE__ */ n(((e, t) => {
	function n() {
		if (typeof __REACT_DEVTOOLS_GLOBAL_HOOK__ < "u" && typeof __REACT_DEVTOOLS_GLOBAL_HOOK__.checkDCE == "function") {
			if (process.env.NODE_ENV !== "production") throw Error("^_^");
			try {
				__REACT_DEVTOOLS_GLOBAL_HOOK__.checkDCE(n);
			} catch (e) {
				console.error(e);
			}
		}
	}
	process.env.NODE_ENV === "production" ? (n(), t.exports = s()) : t.exports = c();
})), u = /* @__PURE__ */ n(((e) => {
	var n = o(), r = t(), i = l();
	function a(e) {
		var t = "https://react.dev/errors/" + e;
		if (1 < arguments.length) {
			t += "?args[]=" + encodeURIComponent(arguments[1]);
			for (var n = 2; n < arguments.length; n++) t += "&args[]=" + encodeURIComponent(arguments[n]);
		}
		return "Minified React error #" + e + "; visit " + t + " for the full message or use the non-minified dev environment for full errors and additional helpful warnings.";
	}
	function s(e) {
		return !(!e || e.nodeType !== 1 && e.nodeType !== 9 && e.nodeType !== 11);
	}
	function c(e) {
		for (var t = e, n = t; n && !n.alternate;) t = n, t.flags & 4098 && (e = t.return), n = t.return;
		for (; t.return;) t = t.return;
		return t.tag === 3 ? e : null;
	}
	function u(e) {
		if (e.tag === 13) {
			var t = e.memoizedState;
			if (t === null && (e = e.alternate, e !== null && (t = e.memoizedState)), t !== null) return t.dehydrated;
		}
		return null;
	}
	function d(e) {
		if (e.tag === 31) {
			var t = e.memoizedState;
			if (t === null && (e = e.alternate, e !== null && (t = e.memoizedState)), t !== null) return t.dehydrated;
		}
		return null;
	}
	function f(e) {
		if (c(e) !== e) throw Error(a(188));
	}
	function p(e) {
		var t = e.alternate;
		if (!t) {
			if (t = c(e), t === null) throw Error(a(188));
			return t === e ? e : null;
		}
		for (var n = e, r = t;;) {
			var i = n.return;
			if (i === null) break;
			var o = i.alternate;
			if (o === null) {
				if (r = i.return, r !== null) {
					n = r;
					continue;
				}
				break;
			}
			if (i.child === o.child) {
				for (o = i.child; o;) {
					if (o === n) return f(i), e;
					if (o === r) return f(i), t;
					o = o.sibling;
				}
				throw Error(a(188));
			}
			if (n.return !== r.return) n = i, r = o;
			else {
				for (var s = !1, l = i.child; l;) {
					if (l === n) {
						s = !0, n = i, r = o;
						break;
					}
					if (l === r) {
						s = !0, r = i, n = o;
						break;
					}
					l = l.sibling;
				}
				if (!s) {
					for (l = o.child; l;) {
						if (l === n) {
							s = !0, n = o, r = i;
							break;
						}
						if (l === r) {
							s = !0, r = o, n = i;
							break;
						}
						l = l.sibling;
					}
					if (!s) throw Error(a(189));
				}
			}
			if (n.alternate !== r) throw Error(a(190));
		}
		if (n.tag !== 3) throw Error(a(188));
		return n.stateNode.current === n ? e : t;
	}
	function m(e) {
		var t = e.tag;
		if (t === 5 || t === 26 || t === 27 || t === 6) return e;
		for (e = e.child; e !== null;) {
			if (t = m(e), t !== null) return t;
			e = e.sibling;
		}
		return null;
	}
	function h(e, t, n, r, i, a) {
		for (; e !== null;) {
			if ((e.tag === 5 || e.tag === 27 || e.tag === 6) && n(e, r, i, a) || (e.tag !== 22 || e.memoizedState === null) && (t || e.tag !== 5 && e.tag !== 27) && h(e.child, t, n, r, i, a)) return !0;
			e = e.sibling;
		}
		return !1;
	}
	function g(e) {
		for (e = e.return; e !== null;) {
			if (e.tag === 3 || e.tag === 5 || e.tag === 27) return e;
			e = e.return;
		}
		return null;
	}
	function _(e) {
		var t = !1;
		for (e = e.return; e !== null && (e.tag === 4 && (t = !0), e.tag !== 3 && e.tag !== 5 && e.tag !== 27);) e = e.return;
		return t;
	}
	function v(e) {
		var t = [null, null], n = g(e);
		return n === null || y(t, e, n.child, { foundSelf: !1 }), t;
	}
	function y(e, t, n, r) {
		for (; n !== null;) {
			if (n === t) r.foundSelf = !0;
			else if (n.tag === 5 || n.tag === 27 || n.tag === 6) {
				if (r.foundSelf) return e[1] = n, !0;
				e[0] = n;
			} else if ((n.tag !== 22 || n.memoizedState === null) && y(e, t, n.child, r)) return !0;
			n = n.sibling;
		}
		return !1;
	}
	function b(e) {
		switch (e.tag) {
			case 5:
			case 27:
			case 6: return e.stateNode;
			case 3: return e.stateNode.containerInfo;
			default: throw Error(a(559));
		}
	}
	var ee = null, te = null;
	function ne(e, t, n) {
		return e === n || e === t && (ee = e, !0);
	}
	function re(e, t, n) {
		return e === n ? (te = e, !1) : e === t && (te !== null && (ee = e), !0);
	}
	function ie(e) {
		if (e === null) return null;
		do
			e = e === null ? null : e.return;
		while (e && e.tag !== 5 && e.tag !== 27 && e.tag !== 3);
		return e || null;
	}
	function ae(e, t, n) {
		for (var r = 0, i = e; i; i = n(i)) r++;
		i = 0;
		for (var a = t; a; a = n(a)) i++;
		for (; 0 < r - i;) e = n(e), r--;
		for (; 0 < i - r;) t = n(t), i--;
		for (; r--;) {
			if (e === t || t !== null && e === t.alternate) return e;
			e = n(e), t = n(t);
		}
		return null;
	}
	var x = Object.assign, oe = Symbol.for("react.element"), se = Symbol.for("react.transitional.element"), ce = Symbol.for("react.portal"), le = Symbol.for("react.fragment"), ue = Symbol.for("react.strict_mode"), de = Symbol.for("react.profiler"), fe = Symbol.for("react.consumer"), pe = Symbol.for("react.context"), me = Symbol.for("react.forward_ref"), he = Symbol.for("react.suspense"), ge = Symbol.for("react.suspense_list"), _e = Symbol.for("react.memo"), ve = Symbol.for("react.lazy"), S = Symbol.for("react.activity"), ye = Symbol.for("react.legacy_hidden"), be = Symbol.for("react.memo_cache_sentinel"), xe = Symbol.for("react.view_transition"), Se = Symbol.for("react.recoverable"), Ce = Symbol.iterator;
	function we(e) {
		return typeof e != "object" || !e ? null : (e = Ce && e[Ce] || e["@@iterator"], typeof e == "function" ? e : null);
	}
	var Te = Symbol.for("react.client.reference");
	function Ee(e) {
		if (e == null) return null;
		if (typeof e == "function") return e.$$typeof === Te ? null : e.displayName || e.name || null;
		if (typeof e == "string") return e;
		switch (e) {
			case le: return "Fragment";
			case de: return "Profiler";
			case ue: return "StrictMode";
			case he: return "Suspense";
			case ge: return "SuspenseList";
			case S: return "Activity";
			case xe: return "ViewTransition";
		}
		if (typeof e == "object") switch (e.$$typeof) {
			case ce: return "Portal";
			case pe: return e.displayName || "Context";
			case fe: return (e._context.displayName || "Context") + ".Consumer";
			case me:
				var t = e.render;
				return e = e.displayName, e ||= (e = t.displayName || t.name || "", e === "" ? "ForwardRef" : "ForwardRef(" + e + ")"), e;
			case _e: return t = e.displayName || null, t === null ? Ee(e.type) || "Memo" : t;
			case ve:
				t = e._payload, e = e._init;
				try {
					return Ee(e(t));
				} catch {}
		}
		return null;
	}
	var De = Array.isArray, C = r.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE, Oe = i.__DOM_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE, ke = {
		pending: !1,
		data: null,
		method: null,
		action: null
	}, Ae = [], je = -1;
	function Me(e) {
		return { current: e };
	}
	function Ne(e) {
		0 > je || (e.current = Ae[je], Ae[je] = null, je--);
	}
	function Pe(e, t) {
		je++, Ae[je] = e.current, e.current = t;
	}
	var Fe = Me(null), Ie = Me(null), Le = Me(null), w = Me(null);
	function Re(e, t) {
		switch (Pe(Le, t), Pe(Ie, e), Pe(Fe, null), t.nodeType) {
			case 9:
			case 11:
				e = (e = t.documentElement) && (e = e.namespaceURI) ? Dp(e) : 0;
				break;
			default: if (e = t.tagName, t = t.namespaceURI) t = Dp(t), e = Op(t, e);
			else switch (e) {
				case "svg":
					e = 1;
					break;
				case "math":
					e = 2;
					break;
				default: e = 0;
			}
		}
		Ne(Fe), Pe(Fe, e);
	}
	function ze() {
		Ne(Fe), Ne(Ie), Ne(Le);
	}
	function Be(e) {
		var t = e.memoizedState;
		t !== null && (xh._currentValue = t.memoizedState, Pe(w, e)), t = Fe.current;
		var n = Op(t, e.type);
		t !== n && (Pe(Ie, e), Pe(Fe, n));
	}
	function Ve(e) {
		Ie.current === e && (Ne(Fe), Ne(Ie)), w.current === e && (Ne(w), xh._currentValue = ke);
	}
	var He, Ue;
	function We(e) {
		if (He === void 0) try {
			throw Error();
		} catch (e) {
			var t = e.stack.trim().match(/\n( *(at )?)/);
			He = t && t[1] || "", Ue = -1 < e.stack.indexOf("\n    at") ? " (<anonymous>)" : -1 < e.stack.indexOf("@") ? "@unknown:0:0" : "";
		}
		return "\n" + He + e + Ue;
	}
	var Ge = !1;
	function Ke(e, t) {
		if (!e || Ge) return "";
		Ge = !0;
		var n = Error.prepareStackTrace;
		Error.prepareStackTrace = void 0;
		try {
			var r = { DetermineComponentFrameRoot: function() {
				try {
					if (t) {
						var n = function() {
							throw Error();
						};
						if (Object.defineProperty(n.prototype, "props", { set: function() {
							throw Error();
						} }), typeof Reflect == "object" && Reflect.construct) {
							try {
								Reflect.construct(n, []);
							} catch (e) {
								var r = e;
							}
							Reflect.construct(e, [], n);
						} else {
							try {
								n.call();
							} catch (e) {
								r = e;
							}
							n = !1;
							try {
								var i = Object.getOwnPropertyDescriptor(e.prototype, "props");
								Object.defineProperty(e.prototype, "props", {
									configurable: !0,
									set: function() {
										throw Error();
									}
								}), n = !0, new e();
							} finally {
								n && (i === void 0 ? delete e.prototype.props : Object.defineProperty(e.prototype, "props", i));
							}
						}
					} else {
						try {
							throw Error();
						} catch (e) {
							r = e;
						}
						(n = e()) && typeof n.catch == "function" && n.catch(function() {});
					}
				} catch (e) {
					if (e && r && typeof e.stack == "string") return [e.stack, r.stack];
				}
				return [null, null];
			} };
			r.DetermineComponentFrameRoot.displayName = "DetermineComponentFrameRoot";
			var i = Object.getOwnPropertyDescriptor(r.DetermineComponentFrameRoot, "name");
			i && i.configurable && Object.defineProperty(r.DetermineComponentFrameRoot, "name", { value: "DetermineComponentFrameRoot" });
			var a = r.DetermineComponentFrameRoot(), o = a[0], s = a[1];
			if (o && s) {
				var c = o.split("\n"), l = s.split("\n");
				for (i = r = 0; r < c.length && !c[r].includes("DetermineComponentFrameRoot");) r++;
				for (; i < l.length && !l[i].includes("DetermineComponentFrameRoot");) i++;
				if (r === c.length || i === l.length) for (r = c.length - 1, i = l.length - 1; 1 <= r && 0 <= i && c[r] !== l[i];) i--;
				for (; 1 <= r && 0 <= i; r--, i--) if (c[r] !== l[i]) {
					if (r !== 1 || i !== 1) do
						if (r--, i--, 0 > i || c[r] !== l[i]) {
							var u = "\n" + c[r].replace(" at new ", " at ");
							return e.displayName && u.includes("<anonymous>") && (u = u.replace("<anonymous>", e.displayName)), u;
						}
					while (1 <= r && 0 <= i);
					break;
				}
			}
		} finally {
			Ge = !1, Error.prepareStackTrace = n;
		}
		return (n = e ? e.displayName || e.name : "") ? We(n) : "";
	}
	function qe(e, t) {
		switch (e.tag) {
			case 26:
			case 27:
			case 5: return We(e.type);
			case 16: return We("Lazy");
			case 13: return e.child !== t && t !== null ? We("Suspense Fallback") : We("Suspense");
			case 19: return We("SuspenseList");
			case 0:
			case 15: return Ke(e.type, !1);
			case 11: return Ke(e.type.render, !1);
			case 1: return Ke(e.type, !0);
			case 31: return We("Activity");
			case 30: return We("ViewTransition");
			default: return "";
		}
	}
	function Je(e) {
		try {
			var t = "", n = null;
			do
				t += qe(e, n), n = e, e = e.return;
			while (e);
			return t;
		} catch (e) {
			return "\nError generating stack: " + e.message + "\n" + e.stack;
		}
	}
	var Ye = Object.prototype.hasOwnProperty, Xe = n.unstable_scheduleCallback, Ze = n.unstable_cancelCallback, Qe = n.unstable_shouldYield, $e = n.unstable_requestPaint, et = n.unstable_now, tt = n.unstable_getCurrentPriorityLevel, nt = n.unstable_ImmediatePriority, rt = n.unstable_UserBlockingPriority, it = n.unstable_NormalPriority, at = n.unstable_LowPriority, ot = n.unstable_IdlePriority, st = n.log, ct = n.unstable_setDisableYieldValue, lt = null, ut = null;
	function dt(e) {
		if (typeof st == "function" && ct(e), ut && typeof ut.setStrictMode == "function") try {
			ut.setStrictMode(lt, e);
		} catch {}
	}
	var ft = Math.clz32 ? Math.clz32 : ht, pt = Math.log, mt = Math.LN2;
	function ht(e) {
		return e >>>= 0, e === 0 ? 32 : 31 - (pt(e) / mt | 0) | 0;
	}
	var gt = 256, _t = 262144, vt = 4194304;
	function yt(e) {
		var t = e & 42;
		if (t !== 0) return t;
		switch (e & -e) {
			case 1: return 1;
			case 2: return 2;
			case 4: return 4;
			case 8: return 8;
			case 16: return 16;
			case 32: return 32;
			case 64: return 64;
			case 128: return 128;
			case 256:
			case 512:
			case 1024:
			case 2048:
			case 4096:
			case 8192:
			case 16384:
			case 32768:
			case 65536:
			case 131072: return e & -e;
			case 262144:
			case 524288:
			case 1048576:
			case 2097152: return e & 3932160;
			case 4194304:
			case 8388608:
			case 16777216:
			case 33554432: return e & 62914560;
			case 67108864: return 67108864;
			case 134217728: return 134217728;
			case 268435456: return 268435456;
			case 536870912: return 536870912;
			case 1073741824: return 0;
			default: return e;
		}
	}
	function bt(e, t, n) {
		var r = e.pendingLanes;
		if (r === 0) return 0;
		var i = 0, a = e.suspendedLanes, o = e.pingedLanes;
		e = e.warmLanes;
		var s = r & 134217727;
		return s === 0 ? (s = r & ~a, s === 0 ? o === 0 ? n || (n = r & ~e, n !== 0 && (i = yt(n))) : i = yt(o) : i = yt(s)) : (r = s & ~a, r === 0 ? (o &= s, o === 0 ? n || (n = s & ~e, n !== 0 && (i = yt(n))) : i = yt(o)) : i = yt(r)), i === 0 ? 0 : t !== 0 && t !== i && (t & a) === 0 && (a = i & -i, n = t & -t, a >= n || a === 32 && n & 4194048) ? t : i;
	}
	function xt(e, t) {
		return (e.pendingLanes & ~(e.suspendedLanes & ~e.pingedLanes) & t) === 0;
	}
	function St(e, t) {
		t & 8 && (t |= t & 32);
		var n = e.entangledLanes;
		if (n !== 0) for (e = e.entanglements, n &= t; 0 < n;) {
			var r = 31 - ft(n), i = 1 << r;
			t |= e[r], n &= ~i;
		}
		return t;
	}
	function Ct(e, t) {
		switch (e) {
			case 1:
			case 2:
			case 4:
			case 8:
			case 64: return t + 250;
			case 16:
			case 32:
			case 128:
			case 256:
			case 512:
			case 1024:
			case 2048:
			case 4096:
			case 8192:
			case 16384:
			case 32768:
			case 65536:
			case 131072:
			case 262144:
			case 524288:
			case 1048576:
			case 2097152: return t + 5e3;
			case 4194304:
			case 8388608:
			case 16777216:
			case 33554432: return -1;
			case 67108864:
			case 134217728:
			case 268435456:
			case 536870912:
			case 1073741824: return -1;
			default: return -1;
		}
	}
	function wt() {
		var e = vt;
		return vt <<= 1, !(vt & 62914560) && (vt = 4194304), e;
	}
	function Tt(e) {
		for (var t = [], n = 0; 31 > n; n++) t.push(e);
		return t;
	}
	function Et(e, t) {
		e.pendingLanes |= t, t !== 268435456 && (e.suspendedLanes = 0, e.pingedLanes = 0, e.warmLanes = 0);
	}
	function Dt(e, t, n, r, i, a) {
		var o = e.pendingLanes;
		e.pendingLanes = n, e.suspendedLanes = 0, e.pingedLanes = 0, e.warmLanes = 0, e.expiredLanes &= n, e.entangledLanes &= n, e.errorRecoveryDisabledLanes &= n, e.shellSuspendCounter = 0;
		var s = e.entanglements, c = e.expirationTimes, l = e.hiddenUpdates;
		for (n = o & ~n; 0 < n;) {
			var u = 31 - ft(n), d = 1 << u;
			s[u] = 0, c[u] = -1;
			var f = l[u];
			if (f !== null) for (l[u] = null, u = 0; u < f.length; u++) {
				var p = f[u];
				p !== null && (p.lane &= -536870913);
			}
			n &= ~d;
		}
		r !== 0 && Ot(e, r, 0), a !== 0 && i === 0 && e.tag !== 0 && (e.suspendedLanes |= a & ~(o & ~t));
	}
	function Ot(e, t, n) {
		e.pendingLanes |= t, e.suspendedLanes &= ~t;
		var r = 31 - ft(t);
		e.entangledLanes |= t, e.entanglements[r] = e.entanglements[r] | 1073741824 | n & 261930;
	}
	function kt(e, t) {
		var n = e.entangledLanes |= t;
		for (e = e.entanglements; n;) {
			var r = 31 - ft(n), i = 1 << r;
			i & t | e[r] & t && (e[r] |= t), n &= ~i;
		}
	}
	function At(e, t) {
		var n = t & -t;
		return n = n & 42 ? 1 : jt(n), (n & (e.suspendedLanes | t)) === 0 ? n : 0;
	}
	function jt(e) {
		switch (e) {
			case 2:
				e = 1;
				break;
			case 8:
				e = 4;
				break;
			case 32:
				e = 16;
				break;
			case 256:
			case 512:
			case 1024:
			case 2048:
			case 4096:
			case 8192:
			case 16384:
			case 32768:
			case 65536:
			case 131072:
			case 262144:
			case 524288:
			case 1048576:
			case 2097152:
			case 4194304:
			case 8388608:
			case 16777216:
			case 33554432:
				e = 128;
				break;
			case 268435456:
				e = 134217728;
				break;
			default: e = 0;
		}
		return e;
	}
	function Mt(e) {
		return e &= -e, 2 < e ? 8 < e ? e & 134217727 ? 32 : 268435456 : 8 : 2;
	}
	function Nt() {
		var e = Oe.p;
		return e === 0 ? (e = window.event, e === void 0 ? 32 : Lh(e.type)) : e;
	}
	function Pt(e, t) {
		var n = Oe.p;
		try {
			return Oe.p = e, t();
		} finally {
			Oe.p = n;
		}
	}
	var Ft = Math.random().toString(36).slice(2), It = "__reactFiber$" + Ft, Lt = "__reactProps$" + Ft, Rt = "__reactContainer$" + Ft, zt = "__reactEvents$" + Ft, Bt = "__reactListeners$" + Ft, Vt = "__reactHandles$" + Ft, Ht = "__reactResources$" + Ft, Ut = "__reactMarker$" + Ft, Wt = "__reactLoad$" + Ft;
	function Gt(e) {
		delete e[It], delete e[Lt], delete e[Bt], delete e[Vt];
	}
	function Kt(e) {
		var t;
		if (t = e[It]) return t;
		for (var n = e.parentNode; n;) {
			if (t = n[Rt] || n[It]) {
				if (n = t.alternate, t.child !== null || n !== null && n.child !== null) for (e = Om(e); e !== null;) {
					if (n = e[It]) return n;
					e = Om(e);
				}
				return t;
			}
			e = n, n = e.parentNode;
		}
		return null;
	}
	function qt(e) {
		if (e = e[It] || e[Rt]) {
			var t = e.tag;
			if (t === 5 || t === 6 || t === 13 || t === 31 || t === 26 || t === 27 || t === 3) return e;
		}
		return null;
	}
	function Jt(e) {
		var t = e.tag;
		if (t === 5 || t === 26 || t === 27 || t === 6) return e.stateNode;
		throw Error(a(33));
	}
	function Yt(e) {
		var t = e[Ht];
		return t ||= e[Ht] = {
			hoistableStyles: /* @__PURE__ */ new Map(),
			hoistableScripts: /* @__PURE__ */ new Map()
		}, t;
	}
	function Xt(e) {
		e[Ut] = !0;
	}
	function Zt(e) {
		e[Wt] = void 0;
	}
	var Qt = /* @__PURE__ */ new Set(), $t = {};
	function en(e, t) {
		tn(e, t), tn(e + "Capture", t);
	}
	function tn(e, t) {
		for ($t[e] = t, e = 0; e < t.length; e++) Qt.add(t[e]);
	}
	var nn = RegExp("^[:A-Z_a-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02FF\\u0370-\\u037D\\u037F-\\u1FFF\\u200C-\\u200D\\u2070-\\u218F\\u2C00-\\u2FEF\\u3001-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFFD][:A-Z_a-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02FF\\u0370-\\u037D\\u037F-\\u1FFF\\u200C-\\u200D\\u2070-\\u218F\\u2C00-\\u2FEF\\u3001-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFFD\\-.0-9\\u00B7\\u0300-\\u036F\\u203F-\\u2040]*$"), rn = {}, an = {};
	function on(e) {
		return Ye.call(an, e) ? !0 : Ye.call(rn, e) ? !1 : nn.test(e) ? an[e] = !0 : (rn[e] = !0, !1);
	}
	var T = !1;
	function sn() {
		var e = T;
		return T = !1, e;
	}
	function cn(e, t, n) {
		if (on(t)) {
			if (n === null) e.removeAttribute(t);
			else {
				switch (typeof n) {
					case "undefined":
					case "function":
					case "symbol":
						e.removeAttribute(t);
						return;
					case "boolean":
						var r = t.toLowerCase().slice(0, 5);
						if (r !== "data-" && r !== "aria-") {
							e.removeAttribute(t);
							return;
						}
				}
				e.setAttribute(t, n);
			}
		}
	}
	function ln(e, t, n) {
		if (n === null) e.removeAttribute(t);
		else {
			switch (typeof n) {
				case "undefined":
				case "function":
				case "symbol":
				case "boolean":
					e.removeAttribute(t);
					return;
			}
			e.setAttribute(t, n);
		}
	}
	function un(e, t, n, r) {
		if (r === null) e.removeAttribute(n);
		else {
			switch (typeof r) {
				case "undefined":
				case "function":
				case "symbol":
				case "boolean":
					e.removeAttribute(n);
					return;
			}
			e.setAttributeNS(t, n, r);
		}
	}
	function dn(e) {
		switch (typeof e) {
			case "bigint":
			case "boolean":
			case "number":
			case "string":
			case "undefined": return e;
			case "object": return e;
			default: return "";
		}
	}
	function fn(e) {
		var t = e.type;
		return (e = e.nodeName) && e.toLowerCase() === "input" && (t === "checkbox" || t === "radio");
	}
	function pn(e, t, n) {
		var r = Object.getOwnPropertyDescriptor(e.constructor.prototype, t);
		if (!e.hasOwnProperty(t) && r !== void 0 && typeof r.get == "function" && typeof r.set == "function") {
			var i = r.get, a = r.set;
			return Object.defineProperty(e, t, {
				configurable: !0,
				get: function() {
					return i.call(this);
				},
				set: function(e) {
					n = "" + e, a.call(this, e);
				}
			}), Object.defineProperty(e, t, { enumerable: r.enumerable }), {
				getValue: function() {
					return n;
				},
				setValue: function(e) {
					n = "" + e;
				},
				stopTracking: function() {
					e._valueTracker = null, delete e[t];
				}
			};
		}
	}
	function mn(e) {
		if (!e._valueTracker) {
			var t = fn(e) ? "checked" : "value";
			e._valueTracker = pn(e, t, "" + e[t]);
		}
	}
	function hn(e) {
		if (!e) return !1;
		var t = e._valueTracker;
		if (!t) return !0;
		var n = t.getValue(), r = "";
		return e && (r = fn(e) ? e.checked ? "true" : "false" : e.value), e = r, e !== n && (t.setValue(e), !0);
	}
	var gn = /[\n"\\]/g;
	function _n(e) {
		return e.replace(gn, function(e) {
			return "\\" + e.charCodeAt(0).toString(16) + " ";
		});
	}
	function vn(e, t, n, r, i, a, o, s) {
		e.name = "", o != null && typeof o != "function" && typeof o != "symbol" && typeof o != "boolean" ? e.type = o : e.removeAttribute("type"), t == null ? o !== "submit" && o !== "reset" || e.removeAttribute("value") : o === "number" ? (t === 0 && e.value === "" || e.value != t) && (e.value = "" + dn(t)) : e.value !== "" + dn(t) && (e.value = "" + dn(t)), t == null ? n == null ? r != null && e.removeAttribute("value") : bn(e, dn(n)) : o === "number" && e.value == t ? bn(e, dn(e.value)) : bn(e, dn(t)), i == null && a != null && (e.defaultChecked = !!a), i != null && (e.checked = i && typeof i != "function" && typeof i != "symbol"), s != null && typeof s != "function" && typeof s != "symbol" && typeof s != "boolean" ? e.name = "" + dn(s) : e.removeAttribute("name");
	}
	function yn(e, t, n, r, i, a, o, s) {
		if (a != null && typeof a != "function" && typeof a != "symbol" && typeof a != "boolean" && (e.type = a), t != null || n != null) {
			if (!(a !== "submit" && a !== "reset" || t != null)) {
				mn(e);
				return;
			}
			n = n == null ? "" : "" + dn(n), t = t == null ? n : "" + dn(t), s || t === e.value || (e.value = t), e.defaultValue = t;
		}
		r ??= i, r = typeof r != "function" && typeof r != "symbol" && !!r, e.checked = s ? e.checked : !!r, e.defaultChecked = !!r, o != null && typeof o != "function" && typeof o != "symbol" && typeof o != "boolean" && (e.name = o), mn(e);
	}
	function bn(e, t) {
		e.defaultValue !== "" + t && (e.defaultValue = "" + t);
	}
	function xn(e, t, n, r) {
		if (e = e.options, t) {
			t = {};
			for (var i = 0; i < n.length; i++) t["$" + n[i]] = !0;
			for (n = 0; n < e.length; n++) i = t.hasOwnProperty("$" + e[n].value), e[n].selected !== i && (e[n].selected = i), i && r && (e[n].defaultSelected = !0);
		} else {
			for (n = "" + dn(n), t = null, i = 0; i < e.length; i++) {
				if (e[i].value === n) {
					e[i].selected = !0, r && (e[i].defaultSelected = !0);
					return;
				}
				t !== null || e[i].disabled || (t = e[i]);
			}
			t !== null && (t.selected = !0);
		}
	}
	function Sn(e, t, n) {
		t != null && (t = "" + dn(t), t !== e.value && (e.value = t), n == null) ? e.defaultValue !== t && (e.defaultValue = t) : e.defaultValue = n == null ? "" : "" + dn(n);
	}
	function Cn(e, t, n, r) {
		if (t == null) {
			if (r != null) {
				if (n != null) throw Error(a(92));
				if (De(r)) {
					if (1 < r.length) throw Error(a(93));
					r = r[0];
				}
				n = r;
			}
			n ??= "", t = n;
		}
		n = dn(t), e.defaultValue = n, r = e.textContent, r === n && r !== "" && r !== null && (e.value = r), mn(e);
	}
	function wn(e, t) {
		if (t) {
			var n = e.firstChild;
			if (n && n === e.lastChild && n.nodeType === 3) {
				n.nodeValue = t;
				return;
			}
		}
		e.textContent = t;
	}
	var Tn = new Set("animationIterationCount aspectRatio borderImageOutset borderImageSlice borderImageWidth boxFlex boxFlexGroup boxOrdinalGroup columnCount columns flex flexGrow flexPositive flexShrink flexNegative flexOrder gridArea gridRow gridRowEnd gridRowSpan gridRowStart gridColumn gridColumnEnd gridColumnSpan gridColumnStart fontWeight lineClamp lineHeight opacity order orphans scale tabSize widows zIndex zoom fillOpacity floodOpacity stopOpacity strokeDasharray strokeDashoffset strokeMiterlimit strokeOpacity strokeWidth MozAnimationIterationCount MozBoxFlex MozBoxFlexGroup MozLineClamp msAnimationIterationCount msFlex msZoom msFlexGrow msFlexNegative msFlexOrder msFlexPositive msFlexShrink msGridColumn msGridColumnSpan msGridRow msGridRowSpan WebkitAnimationIterationCount WebkitBoxFlex WebKitBoxFlexGroup WebkitBoxOrdinalGroup WebkitColumnCount WebkitColumns WebkitFlex WebkitFlexGrow WebkitFlexPositive WebkitFlexShrink WebkitLineClamp".split(" "));
	function En(e, t, n) {
		var r = t.indexOf("--") === 0;
		n == null || typeof n == "boolean" || n === "" ? r ? e.setProperty(t, "") : t === "float" ? e.cssFloat = "" : e[t] = "" : r ? e.setProperty(t, n) : typeof n != "number" || n === 0 || Tn.has(t) ? t === "float" ? e.cssFloat = n : e[t] = ("" + n).trim() : e[t] = n + "px";
	}
	function Dn(e, t, n) {
		if (t != null && typeof t != "object") throw Error(a(62));
		if (e = e.style, n != null) {
			for (var r in n) !n.hasOwnProperty(r) || t != null && t.hasOwnProperty(r) || (r.indexOf("--") === 0 ? e.setProperty(r, "") : r === "float" ? e.cssFloat = "" : e[r] = "", T = !0);
			for (var i in t) r = t[i], t.hasOwnProperty(i) && n[i] !== r && (En(e, i, r), T = !0);
		} else for (var o in t) t.hasOwnProperty(o) && En(e, o, t[o]);
	}
	function On(e) {
		if (e.indexOf("-") === -1) return !1;
		switch (e) {
			case "annotation-xml":
			case "color-profile":
			case "font-face":
			case "font-face-src":
			case "font-face-uri":
			case "font-face-format":
			case "font-face-name":
			case "missing-glyph": return !1;
			default: return !0;
		}
	}
	var kn = /* @__PURE__ */ new Map([
		["acceptCharset", "accept-charset"],
		["htmlFor", "for"],
		["httpEquiv", "http-equiv"],
		["crossOrigin", "crossorigin"],
		["accentHeight", "accent-height"],
		["alignmentBaseline", "alignment-baseline"],
		["arabicForm", "arabic-form"],
		["baselineShift", "baseline-shift"],
		["capHeight", "cap-height"],
		["clipPath", "clip-path"],
		["clipRule", "clip-rule"],
		["colorInterpolation", "color-interpolation"],
		["colorInterpolationFilters", "color-interpolation-filters"],
		["colorProfile", "color-profile"],
		["colorRendering", "color-rendering"],
		["dominantBaseline", "dominant-baseline"],
		["enableBackground", "enable-background"],
		["fillOpacity", "fill-opacity"],
		["fillRule", "fill-rule"],
		["floodColor", "flood-color"],
		["floodOpacity", "flood-opacity"],
		["fontFamily", "font-family"],
		["fontSize", "font-size"],
		["fontSizeAdjust", "font-size-adjust"],
		["fontStretch", "font-stretch"],
		["fontStyle", "font-style"],
		["fontVariant", "font-variant"],
		["fontWeight", "font-weight"],
		["glyphName", "glyph-name"],
		["glyphOrientationHorizontal", "glyph-orientation-horizontal"],
		["glyphOrientationVertical", "glyph-orientation-vertical"],
		["horizAdvX", "horiz-adv-x"],
		["horizOriginX", "horiz-origin-x"],
		["imageRendering", "image-rendering"],
		["letterSpacing", "letter-spacing"],
		["lightingColor", "lighting-color"],
		["markerEnd", "marker-end"],
		["markerMid", "marker-mid"],
		["markerStart", "marker-start"],
		["maskType", "mask-type"],
		["overlinePosition", "overline-position"],
		["overlineThickness", "overline-thickness"],
		["paintOrder", "paint-order"],
		["panose-1", "panose-1"],
		["pointerEvents", "pointer-events"],
		["renderingIntent", "rendering-intent"],
		["shapeRendering", "shape-rendering"],
		["stopColor", "stop-color"],
		["stopOpacity", "stop-opacity"],
		["strikethroughPosition", "strikethrough-position"],
		["strikethroughThickness", "strikethrough-thickness"],
		["strokeDasharray", "stroke-dasharray"],
		["strokeDashoffset", "stroke-dashoffset"],
		["strokeLinecap", "stroke-linecap"],
		["strokeLinejoin", "stroke-linejoin"],
		["strokeMiterlimit", "stroke-miterlimit"],
		["strokeOpacity", "stroke-opacity"],
		["strokeWidth", "stroke-width"],
		["textAnchor", "text-anchor"],
		["textDecoration", "text-decoration"],
		["textRendering", "text-rendering"],
		["transformOrigin", "transform-origin"],
		["underlinePosition", "underline-position"],
		["underlineThickness", "underline-thickness"],
		["unicodeBidi", "unicode-bidi"],
		["unicodeRange", "unicode-range"],
		["unitsPerEm", "units-per-em"],
		["vAlphabetic", "v-alphabetic"],
		["vHanging", "v-hanging"],
		["vIdeographic", "v-ideographic"],
		["vMathematical", "v-mathematical"],
		["vectorEffect", "vector-effect"],
		["vertAdvY", "vert-adv-y"],
		["vertOriginX", "vert-origin-x"],
		["vertOriginY", "vert-origin-y"],
		["wordSpacing", "word-spacing"],
		["writingMode", "writing-mode"],
		["xmlnsXlink", "xmlns:xlink"],
		["xHeight", "x-height"]
	]), An = /^[\u0000-\u001F ]*j[\r\n\t]*a[\r\n\t]*v[\r\n\t]*a[\r\n\t]*s[\r\n\t]*c[\r\n\t]*r[\r\n\t]*i[\r\n\t]*p[\r\n\t]*t[\r\n\t]*:/i;
	function jn(e) {
		return An.test("" + e) ? "javascript:throw new Error('React has blocked a javascript: URL as a security precaution.')" : e;
	}
	function Mn() {}
	var Nn = null;
	function Pn(e) {
		return e = e.target || e.srcElement || window, e.correspondingUseElement && (e = e.correspondingUseElement), e.nodeType === 3 ? e.parentNode : e;
	}
	var Fn = null, In = null;
	function Ln(e) {
		var t = qt(e);
		if (t && (e = t.stateNode)) {
			var n = e[Lt] || null;
			a: switch (e = t.stateNode, t.type) {
				case "input":
					if (vn(e, n.value, n.defaultValue, n.defaultValue, n.checked, n.defaultChecked, n.type, n.name), t = n.name, n.type === "radio" && t != null) {
						for (n = e; n.parentNode;) n = n.parentNode;
						for (n = n.querySelectorAll("input[name=\"" + _n("" + t) + "\"][type=\"radio\"]"), t = 0; t < n.length; t++) {
							var r = n[t];
							if (r !== e && r.form === e.form) {
								var i = r[Lt] || null;
								if (!i) throw Error(a(90));
								vn(r, i.value, i.defaultValue, i.defaultValue, i.checked, i.defaultChecked, i.type, i.name);
							}
						}
						for (t = 0; t < n.length; t++) r = n[t], r.form === e.form && hn(r);
					}
					break a;
				case "textarea":
					Sn(e, n.value, n.defaultValue);
					break a;
				case "select": t = n.value, t != null && xn(e, !!n.multiple, t, !1);
			}
		}
	}
	var Rn = !1;
	function zn(e, t, n) {
		if (Rn) return e(t, n);
		Rn = !0;
		try {
			return e(t);
		} finally {
			if (Rn = !1, (Fn !== null || In !== null) && (ef(), Fn && (t = Fn, e = In, In = Fn = null, Ln(t), e))) for (t = 0; t < e.length; t++) Ln(e[t]);
		}
	}
	function Bn(e, t) {
		var n = e.stateNode;
		if (n === null) return null;
		var r = n[Lt] || null;
		if (r === null) return null;
		n = r[t];
		a: switch (t) {
			case "onClick":
			case "onClickCapture":
			case "onDoubleClick":
			case "onDoubleClickCapture":
			case "onMouseDown":
			case "onMouseDownCapture":
			case "onMouseMove":
			case "onMouseMoveCapture":
			case "onMouseUp":
			case "onMouseUpCapture":
			case "onMouseEnter":
				(r = !r.disabled) || (e = e.type, r = e !== "button" && e !== "input" && e !== "select" && e !== "textarea"), e = !r;
				break a;
			default: e = !1;
		}
		if (e) return null;
		if (n && typeof n != "function") throw Error(a(231, t, typeof n));
		return n;
	}
	var Vn = typeof window < "u" && window.document !== void 0 && window.document.createElement !== void 0, Hn = !1;
	if (Vn) try {
		var Un = {};
		Object.defineProperty(Un, "passive", { get: function() {
			Hn = !0;
		} }), window.addEventListener("test", Un, Un), window.removeEventListener("test", Un, Un);
	} catch {
		Hn = !1;
	}
	var Wn = null, Gn = null, Kn = null;
	function qn() {
		if (Kn) return Kn;
		var e, t = Gn, n = t.length, r, i = "value" in Wn ? Wn.value : Wn.textContent, a = i.length;
		for (e = 0; e < n && t[e] === i[e]; e++);
		var o = n - e;
		for (r = 1; r <= o && t[n - r] === i[a - r]; r++);
		return Kn = i.slice(e, 1 < r ? 1 - r : void 0);
	}
	function Jn(e) {
		var t = e.keyCode;
		return "charCode" in e ? (e = e.charCode, e === 0 && t === 13 && (e = 13)) : e = t, e === 10 && (e = 13), 32 <= e || e === 13 ? e : 0;
	}
	function Yn() {
		return !0;
	}
	function Xn() {
		return !1;
	}
	function Zn(e) {
		function t(t, n, r, i, a) {
			for (var o in this._reactName = t, this._targetInst = r, this.type = n, this.nativeEvent = i, this.target = a, this.currentTarget = null, e) e.hasOwnProperty(o) && (t = e[o], this[o] = t ? t(i) : i[o]);
			return this.isDefaultPrevented = (i.defaultPrevented == null ? !1 === i.returnValue : i.defaultPrevented) ? Yn : Xn, this.isPropagationStopped = Xn, this;
		}
		return x(t.prototype, {
			preventDefault: function() {
				this.defaultPrevented = !0;
				var e = this.nativeEvent;
				e && (e.preventDefault ? e.preventDefault() : typeof e.returnValue != "unknown" && (e.returnValue = !1), this.isDefaultPrevented = Yn);
			},
			stopPropagation: function() {
				var e = this.nativeEvent;
				e && (e.stopPropagation ? e.stopPropagation() : typeof e.cancelBubble != "unknown" && (e.cancelBubble = !0), this.isPropagationStopped = Yn);
			},
			persist: function() {},
			isPersistent: Yn
		}), t;
	}
	var Qn = {
		eventPhase: 0,
		bubbles: 0,
		cancelable: 0,
		timeStamp: function(e) {
			return e.timeStamp || Date.now();
		},
		defaultPrevented: 0,
		isTrusted: 0
	}, $n = Zn(Qn), er = x({}, Qn, {
		view: 0,
		detail: 0
	}), tr = Zn(er), nr, rr, ir, ar = x({}, er, {
		screenX: 0,
		screenY: 0,
		clientX: 0,
		clientY: 0,
		pageX: 0,
		pageY: 0,
		ctrlKey: 0,
		shiftKey: 0,
		altKey: 0,
		metaKey: 0,
		getModifierState: gr,
		button: 0,
		buttons: 0,
		relatedTarget: function(e) {
			return e.relatedTarget === void 0 ? e.fromElement === e.srcElement ? e.toElement : e.fromElement : e.relatedTarget;
		},
		movementX: function(e) {
			return "movementX" in e ? e.movementX : (e !== ir && (ir && e.type === "mousemove" ? (nr = e.screenX - ir.screenX, rr = e.screenY - ir.screenY) : rr = nr = 0, ir = e), nr);
		},
		movementY: function(e) {
			return "movementY" in e ? e.movementY : rr;
		}
	}), or = Zn(ar), sr = Zn(x({}, ar, { dataTransfer: 0 })), cr = Zn(x({}, er, { relatedTarget: 0 })), lr = Zn(x({}, Qn, {
		animationName: 0,
		elapsedTime: 0,
		pseudoElement: 0
	})), ur = Zn(x({}, Qn, { clipboardData: function(e) {
		return "clipboardData" in e ? e.clipboardData : window.clipboardData;
	} })), dr = Zn(x({}, Qn, { data: 0 })), fr = {
		Esc: "Escape",
		Spacebar: " ",
		Left: "ArrowLeft",
		Up: "ArrowUp",
		Right: "ArrowRight",
		Down: "ArrowDown",
		Del: "Delete",
		Win: "OS",
		Menu: "ContextMenu",
		Apps: "ContextMenu",
		Scroll: "ScrollLock",
		MozPrintableKey: "Unidentified"
	}, pr = {
		8: "Backspace",
		9: "Tab",
		12: "Clear",
		13: "Enter",
		16: "Shift",
		17: "Control",
		18: "Alt",
		19: "Pause",
		20: "CapsLock",
		27: "Escape",
		32: " ",
		33: "PageUp",
		34: "PageDown",
		35: "End",
		36: "Home",
		37: "ArrowLeft",
		38: "ArrowUp",
		39: "ArrowRight",
		40: "ArrowDown",
		45: "Insert",
		46: "Delete",
		112: "F1",
		113: "F2",
		114: "F3",
		115: "F4",
		116: "F5",
		117: "F6",
		118: "F7",
		119: "F8",
		120: "F9",
		121: "F10",
		122: "F11",
		123: "F12",
		144: "NumLock",
		145: "ScrollLock",
		224: "Meta"
	}, mr = {
		Alt: "altKey",
		Control: "ctrlKey",
		Meta: "metaKey",
		Shift: "shiftKey"
	};
	function hr(e) {
		var t = this.nativeEvent;
		return t.getModifierState ? t.getModifierState(e) : (e = mr[e]) ? !!t[e] : !1;
	}
	function gr() {
		return hr;
	}
	var _r = Zn(x({}, er, {
		key: function(e) {
			if (e.key) {
				var t = fr[e.key] || e.key;
				if (t !== "Unidentified") return t;
			}
			return e.type === "keypress" ? (e = Jn(e), e === 13 ? "Enter" : String.fromCharCode(e)) : e.type === "keydown" || e.type === "keyup" ? pr[e.keyCode] || "Unidentified" : "";
		},
		code: 0,
		location: 0,
		ctrlKey: 0,
		shiftKey: 0,
		altKey: 0,
		metaKey: 0,
		repeat: 0,
		locale: 0,
		getModifierState: gr,
		charCode: function(e) {
			return e.type === "keypress" ? Jn(e) : 0;
		},
		keyCode: function(e) {
			return e.type === "keydown" || e.type === "keyup" ? e.keyCode : 0;
		},
		which: function(e) {
			return e.type === "keypress" ? Jn(e) : e.type === "keydown" || e.type === "keyup" ? e.keyCode : 0;
		}
	})), vr = Zn(x({}, ar, {
		pointerId: 0,
		width: 0,
		height: 0,
		pressure: 0,
		tangentialPressure: 0,
		tiltX: 0,
		tiltY: 0,
		twist: 0,
		pointerType: 0,
		isPrimary: 0
	})), yr = Zn(x({}, Qn, { submitter: 0 })), br = Zn(x({}, er, {
		touches: 0,
		targetTouches: 0,
		changedTouches: 0,
		altKey: 0,
		metaKey: 0,
		ctrlKey: 0,
		shiftKey: 0,
		getModifierState: gr
	})), xr = Zn(x({}, Qn, {
		propertyName: 0,
		elapsedTime: 0,
		pseudoElement: 0
	})), Sr = Zn(x({}, ar, {
		deltaX: function(e) {
			return "deltaX" in e ? e.deltaX : "wheelDeltaX" in e ? -e.wheelDeltaX : 0;
		},
		deltaY: function(e) {
			return "deltaY" in e ? e.deltaY : "wheelDeltaY" in e ? -e.wheelDeltaY : "wheelDelta" in e ? -e.wheelDelta : 0;
		},
		deltaZ: 0,
		deltaMode: 0
	})), Cr = Zn(x({}, Qn, {
		newState: 0,
		oldState: 0,
		source: 0
	})), wr = [
		9,
		13,
		27,
		32
	], Tr = Vn && "CompositionEvent" in window, Er = null;
	Vn && "documentMode" in document && (Er = document.documentMode);
	var Dr = Vn && "TextEvent" in window && !Er, Or = Vn && (!Tr || Er && 8 < Er && 11 >= Er), kr = " ", Ar = !1;
	function jr(e, t) {
		switch (e) {
			case "keyup": return wr.indexOf(t.keyCode) !== -1;
			case "keydown": return t.keyCode !== 229;
			case "keypress":
			case "mousedown":
			case "focusout": return !0;
			default: return !1;
		}
	}
	function Mr(e) {
		return e = e.detail, typeof e == "object" && "data" in e ? e.data : null;
	}
	var Nr = !1;
	function Pr(e, t) {
		switch (e) {
			case "compositionend": return Mr(t);
			case "keypress": return t.which === 32 ? (Ar = !0, kr) : null;
			case "textInput": return e = t.data, e === kr && Ar ? null : e;
			default: return null;
		}
	}
	function Fr(e, t) {
		if (Nr) return e === "compositionend" || !Tr && jr(e, t) ? (e = qn(), Kn = Gn = Wn = null, Nr = !1, e) : null;
		switch (e) {
			case "paste": return null;
			case "keypress":
				if (!(t.ctrlKey || t.altKey || t.metaKey) || t.ctrlKey && t.altKey) {
					if (t.char && 1 < t.char.length) return t.char;
					if (t.which) return String.fromCharCode(t.which);
				}
				return null;
			case "compositionend": return Or && t.locale !== "ko" ? null : t.data;
			default: return null;
		}
	}
	var Ir = {
		color: !0,
		date: !0,
		datetime: !0,
		"datetime-local": !0,
		email: !0,
		month: !0,
		number: !0,
		password: !0,
		range: !0,
		search: !0,
		tel: !0,
		text: !0,
		time: !0,
		url: !0,
		week: !0
	};
	function Lr(e) {
		var t = e && e.nodeName && e.nodeName.toLowerCase();
		return t === "input" ? !!Ir[e.type] : t === "textarea";
	}
	function Rr(e, t, n, r) {
		Fn ? In ? In.push(r) : In = [r] : Fn = r, t = up(t, "onChange"), 0 < t.length && (n = new $n("onChange", "change", null, n, r), e.push({
			event: n,
			listeners: t
		}));
	}
	var zr = null, Br = null;
	function Vr(e) {
		rp(e, 0);
	}
	function Hr(e) {
		if (hn(Jt(e))) return e;
	}
	function Ur(e, t) {
		if (e === "change") return t;
	}
	var Wr = !1;
	if (Vn) {
		var Gr;
		if (Vn) {
			var Kr = "oninput" in document;
			if (!Kr) {
				var qr = document.createElement("div");
				qr.setAttribute("oninput", "return;"), Kr = typeof qr.oninput == "function";
			}
			Gr = Kr;
		} else Gr = !1;
		Wr = Gr && (!document.documentMode || 9 < document.documentMode);
	}
	function Jr() {
		zr && (zr.detachEvent("onpropertychange", Yr), Br = zr = null);
	}
	function Yr(e) {
		if (e.propertyName === "value" && Hr(Br)) {
			var t = [];
			Rr(t, Br, e, Pn(e)), zn(Vr, t);
		}
	}
	function Xr(e, t, n) {
		e === "focusin" ? (Jr(), zr = t, Br = n, zr.attachEvent("onpropertychange", Yr)) : e === "focusout" && Jr();
	}
	function Zr(e) {
		if (e === "selectionchange" || e === "keyup" || e === "keydown") return Hr(Br);
	}
	function Qr(e, t) {
		if (e === "click") return Hr(t);
	}
	function $r(e, t) {
		if (e === "input" || e === "change") return Hr(t);
	}
	function ei(e, t) {
		return e === t && (e !== 0 || 1 / e == 1 / t) || e !== e && t !== t;
	}
	var ti = typeof Object.is == "function" ? Object.is : ei;
	function ni(e, t) {
		if (ti(e, t)) return !0;
		if (typeof e != "object" || !e || typeof t != "object" || !t) return !1;
		var n = Object.keys(e), r = Object.keys(t);
		if (n.length !== r.length) return !1;
		for (r = 0; r < n.length; r++) {
			var i = n[r];
			if (!Ye.call(t, i) || !ti(e[i], t[i])) return !1;
		}
		return !0;
	}
	function ri(e) {
		if (e ||= typeof document < "u" ? document : void 0, e === void 0) return null;
		try {
			return e.activeElement || e.body;
		} catch {
			return e.body;
		}
	}
	function ii(e) {
		for (; e && e.firstChild;) e = e.firstChild;
		return e;
	}
	function ai(e, t) {
		var n = ii(e);
		e = 0;
		for (var r; n;) {
			if (n.nodeType === 3) {
				if (r = e + n.textContent.length, e <= t && r >= t) return {
					node: n,
					offset: t - e
				};
				e = r;
			}
			a: {
				for (; n;) {
					if (n.nextSibling) {
						n = n.nextSibling;
						break a;
					}
					n = n.parentNode;
				}
				n = void 0;
			}
			n = ii(n);
		}
	}
	function oi(e, t) {
		return e && t ? e === t ? !0 : e && e.nodeType === 3 ? !1 : t && t.nodeType === 3 ? oi(e, t.parentNode) : "contains" in e ? e.contains(t) : e.compareDocumentPosition ? !!(e.compareDocumentPosition(t) & 16) : !1 : !1;
	}
	function si(e) {
		e = e != null && e.ownerDocument != null && e.ownerDocument.defaultView != null ? e.ownerDocument.defaultView : window;
		for (var t = ri(e.document); t instanceof e.HTMLIFrameElement;) {
			try {
				var n = typeof t.contentWindow.location.href == "string";
			} catch {
				n = !1;
			}
			if (n) e = t.contentWindow;
			else break;
			t = ri(e.document);
		}
		return t;
	}
	function ci(e) {
		var t = e && e.nodeName && e.nodeName.toLowerCase();
		return t && (t === "input" && (e.type === "text" || e.type === "search" || e.type === "tel" || e.type === "url" || e.type === "password") || t === "textarea" || e.contentEditable === "true");
	}
	var li = Vn && "documentMode" in document && 11 >= document.documentMode, ui = null, di = null, fi = null, pi = !1;
	function mi(e, t, n) {
		var r = n.window === n ? n.document : n.nodeType === 9 ? n : n.ownerDocument;
		pi || ui == null || ui !== ri(r) || (r = ui, "selectionStart" in r && ci(r) ? r = {
			start: r.selectionStart,
			end: r.selectionEnd
		} : (r = (r.ownerDocument && r.ownerDocument.defaultView || window).getSelection(), r = {
			anchorNode: r.anchorNode,
			anchorOffset: r.anchorOffset,
			focusNode: r.focusNode,
			focusOffset: r.focusOffset
		}), fi && ni(fi, r) || (fi = r, r = up(di, "onSelect"), 0 < r.length && (t = new $n("onSelect", "select", null, t, n), e.push({
			event: t,
			listeners: r
		}), t.target = ui)));
	}
	function hi(e, t) {
		var n = {};
		return n[e.toLowerCase()] = t.toLowerCase(), n["Webkit" + e] = "webkit" + t, n["Moz" + e] = "moz" + t, n;
	}
	var gi = {
		animationend: hi("Animation", "AnimationEnd"),
		animationiteration: hi("Animation", "AnimationIteration"),
		animationstart: hi("Animation", "AnimationStart"),
		transitionrun: hi("Transition", "TransitionRun"),
		transitionstart: hi("Transition", "TransitionStart"),
		transitioncancel: hi("Transition", "TransitionCancel"),
		transitionend: hi("Transition", "TransitionEnd")
	}, _i = {}, vi = {};
	Vn && (vi = document.createElement("div").style, "AnimationEvent" in window || (delete gi.animationend.animation, delete gi.animationiteration.animation, delete gi.animationstart.animation), "TransitionEvent" in window || delete gi.transitionend.transition);
	function yi(e) {
		if (_i[e]) return _i[e];
		if (!gi[e]) return e;
		var t = gi[e], n;
		for (n in t) if (t.hasOwnProperty(n) && n in vi) return _i[e] = t[n];
		return e;
	}
	var bi = yi("animationend"), xi = yi("animationiteration"), Si = yi("animationstart"), Ci = yi("transitionrun"), wi = yi("transitionstart"), Ti = yi("transitioncancel"), Ei = yi("transitionend"), Di = /* @__PURE__ */ new Map(), Oi = "abort auxClick beforeToggle cancel canPlay canPlayThrough click close contextMenu copy cut drag dragEnd dragEnter dragExit dragLeave dragOver dragStart drop durationChange emptied encrypted ended error fullscreenChange fullscreenError gotPointerCapture input invalid keyDown keyPress keyUp load loadedData loadedMetadata loadStart lostPointerCapture mouseDown mouseMove mouseOut mouseOver mouseUp paste pause play playing pointerCancel pointerDown pointerMove pointerOut pointerOver pointerUp progress rateChange reset resize seeked seeking stalled submit suspend timeUpdate touchCancel touchEnd touchStart volumeChange scroll toggle touchMove waiting wheel".split(" ");
	Oi.push("scrollEnd");
	function ki(e, t) {
		Di.set(e, t), en(t, [e]);
	}
	var Ai = 0;
	function ji(e, t) {
		if (e.name != null && e.name !== "auto") return e.name;
		if (t.autoName !== null) return t.autoName;
		e = Fd.identifierPrefix;
		var n = Ai++;
		return e = "_" + e + "t_" + n.toString(32) + "_", t.autoName = e;
	}
	function Mi(e) {
		if (e == null || typeof e == "string") return e;
		var t = null, n = Ud;
		if (n !== null) for (var r = 0; r < n.length; r++) {
			var i = e[n[r]];
			if (i != null) {
				if (i === "none") return "none";
				t = t == null ? i : t + (" " + i);
			}
		}
		return t ?? e.default;
	}
	function Ni(e, t) {
		return e = Mi(e), t = Mi(t), t == null ? e === "auto" ? null : e : t === "auto" ? null : t;
	}
	var Pi = typeof reportError == "function" ? reportError : function(e) {
		if (typeof window == "object" && typeof window.ErrorEvent == "function") {
			var t = new window.ErrorEvent("error", {
				bubbles: !0,
				cancelable: !0,
				message: typeof e == "object" && e && typeof e.message == "string" ? String(e.message) : String(e),
				error: e
			});
			if (!window.dispatchEvent(t)) return;
		} else if (typeof process == "object" && typeof process.emit == "function") {
			process.emit("uncaughtException", e);
			return;
		}
		console.error(e);
	}, Fi = [], Ii = 0, Li = 0;
	function Ri() {
		for (var e = Ii, t = Li = Ii = 0; t < e;) {
			var n = Fi[t];
			Fi[t++] = null;
			var r = Fi[t];
			Fi[t++] = null;
			var i = Fi[t];
			Fi[t++] = null;
			var a = Fi[t];
			if (Fi[t++] = null, r !== null && i !== null) {
				var o = r.pending;
				o === null ? i.next = i : (i.next = o.next, o.next = i), r.pending = i;
			}
			a !== 0 && Hi(n, i, a);
		}
	}
	function zi(e, t, n, r) {
		Fi[Ii++] = e, Fi[Ii++] = t, Fi[Ii++] = n, Fi[Ii++] = r, Li |= r, e.lanes |= r, e = e.alternate, e !== null && (e.lanes |= r);
	}
	function Bi(e, t, n, r) {
		return zi(e, t, n, r), Ui(e);
	}
	function Vi(e, t) {
		return zi(e, null, null, t), Ui(e);
	}
	function Hi(e, t, n) {
		e.lanes |= n;
		var r = e.alternate;
		r !== null && (r.lanes |= n);
		for (var i = !1, a = e.return; a !== null;) a.childLanes |= n, r = a.alternate, r !== null && (r.childLanes |= n), a.tag === 22 && (e = a.stateNode, e === null || e._visibility & 1 || (i = !0)), e = a, a = a.return;
		return e.tag === 3 ? (a = e.stateNode, i && t !== null && (i = 31 - ft(n), e = a.hiddenUpdates, r = e[i], r === null ? e[i] = [t] : r.push(t), t.lane = n | 536870912), a) : null;
	}
	function Ui(e) {
		if (50 < Wd) throw Wd = 0, Gd = null, Error(a(185));
		for (var t = e.return; t !== null;) e = t, t = e.return;
		return e.tag === 3 ? e.stateNode : null;
	}
	var Wi = {};
	function Gi(e, t, n, r) {
		this.tag = e, this.key = n, this.sibling = this.child = this.return = this.stateNode = this.type = this.elementType = null, this.index = 0, this.refCleanup = this.ref = null, this.pendingProps = t, this.dependencies = this.memoizedState = this.updateQueue = this.memoizedProps = null, this.mode = r, this.subtreeFlags = this.flags = 0, this.deletions = null, this.childLanes = this.lanes = 0, this.alternate = null;
	}
	function Ki(e, t, n, r) {
		return new Gi(e, t, n, r);
	}
	function qi(e) {
		return e = e.prototype, !(!e || !e.isReactComponent);
	}
	function Ji(e, t) {
		var n = e.alternate;
		return n === null ? (n = Ki(e.tag, t, e.key, e.mode), n.elementType = e.elementType, n.type = e.type, n.stateNode = e.stateNode, n.alternate = e, e.alternate = n) : (n.pendingProps = t, n.type = e.type, n.flags = 0, n.subtreeFlags = 0, n.deletions = null), n.flags = e.flags & 1206910976, n.childLanes = e.childLanes, n.lanes = e.lanes, n.child = e.child, n.memoizedProps = e.memoizedProps, n.memoizedState = e.memoizedState, n.updateQueue = e.updateQueue, t = e.dependencies, n.dependencies = t === null ? null : {
			lanes: t.lanes,
			firstContext: t.firstContext
		}, n.sibling = e.sibling, n.index = e.index, n.ref = e.ref, n.refCleanup = e.refCleanup, n;
	}
	function Yi(e, t) {
		e.flags &= 1206910978;
		var n = e.alternate;
		return n === null ? (e.childLanes = 0, e.lanes = t, e.child = null, e.subtreeFlags = 0, e.memoizedProps = null, e.memoizedState = null, e.updateQueue = null, e.dependencies = null, e.stateNode = null) : (e.childLanes = n.childLanes, e.lanes = n.lanes, e.child = n.child, e.subtreeFlags = 0, e.deletions = null, e.memoizedProps = n.memoizedProps, e.memoizedState = n.memoizedState, e.updateQueue = n.updateQueue, e.type = n.type, t = n.dependencies, e.dependencies = t === null ? null : {
			lanes: t.lanes,
			firstContext: t.firstContext
		}), e;
	}
	function Xi(e, t, n, r, i, o) {
		var s = 0;
		if (r = e, typeof r == "function") qi(r) && (s = 1);
		else if (typeof r == "string") s = sh(e, n, Fe.current) ? 26 : e === "html" || e === "head" || e === "body" ? 27 : 5;
		else a: switch (r) {
			case S: return e = Ki(31, n, t, i), e.elementType = S, e.lanes = o, e;
			case le: return Zi(n.children, i, o, t);
			case ue:
				s = 8, i |= 24;
				break;
			case de: return e = Ki(12, n, t, i | 2), e.elementType = de, e.lanes = o, e;
			case he: return e = Ki(13, n, t, i), e.elementType = he, e.lanes = o, e;
			case ge: return e = Ki(19, n, t, i), e.elementType = ge, e.lanes = o, e;
			case ye:
			case xe: return e = i | 32, e = Ki(30, n, t, e), e.elementType = xe, e.lanes = o, e.stateNode = {
				autoName: null,
				paired: null,
				clones: null,
				ref: null
			}, e;
			default:
				if (typeof r == "object" && r) switch (r.$$typeof) {
					case pe:
						s = 10;
						break a;
					case fe:
						s = 9;
						break a;
					case me:
						s = 11;
						break a;
					case _e:
						s = 14;
						break a;
					case ve:
						s = 16, r = null;
						break a;
				}
				s = 29, n = Error(a(130, e === null ? "null" : typeof e, "")), r = null;
		}
		return t = Ki(s, n, t, i), t.elementType = e, t.type = r, t.lanes = o, t;
	}
	function Zi(e, t, n, r) {
		return e = Ki(7, e, r, t), e.lanes = n, e;
	}
	function Qi(e, t, n) {
		return e = Ki(6, e, null, t), e.lanes = n, e;
	}
	function $i(e) {
		var t = Ki(18, null, null, 0);
		return t.stateNode = e, t;
	}
	function ea(e, t, n) {
		return t = Ki(4, e.children === null ? [] : e.children, e.key, t), t.lanes = n, t.stateNode = {
			containerInfo: e.containerInfo,
			pendingChildren: null,
			implementation: e.implementation
		}, t;
	}
	var ta = /* @__PURE__ */ new WeakMap();
	function na(e, t) {
		if (typeof e == "object" && e) {
			var n = ta.get(e);
			return n === void 0 ? (t = {
				value: e,
				source: t,
				stack: Je(t)
			}, ta.set(e, t), t) : n;
		}
		return {
			value: e,
			source: t,
			stack: Je(t)
		};
	}
	var ra = [], ia = 0, aa = null, oa = 0, sa = [], ca = 0, la = null, ua = 1, da = "";
	function fa(e, t) {
		ra[ia++] = oa, ra[ia++] = aa, aa = e, oa = t;
	}
	function pa(e, t, n) {
		sa[ca++] = ua, sa[ca++] = da, sa[ca++] = la, la = e;
		var r = ua;
		e = da;
		var i = 32 - ft(r) - 1;
		r &= ~(1 << i), n += 1;
		var a = 32 - ft(t) + i;
		if (30 < a) {
			var o = i - i % 5;
			a = (r & (1 << o) - 1).toString(32), r >>= o, i -= o, ua = 1 << 32 - ft(t) + i | n << i | r, da = a + e;
		} else ua = 1 << a | n << i | r, da = e;
	}
	function ma(e) {
		e.return !== null && (fa(e, 1), pa(e, 1, 0));
	}
	function ha(e) {
		for (; e === aa;) aa = ra[--ia], ra[ia] = null, oa = ra[--ia], ra[ia] = null;
		for (; e === la;) la = sa[--ca], sa[ca] = null, da = sa[--ca], sa[ca] = null, ua = sa[--ca], sa[ca] = null;
	}
	function ga(e, t) {
		sa[ca++] = ua, sa[ca++] = da, sa[ca++] = la, ua = t.id, da = t.overflow, la = e;
	}
	var _a = null, va = null, E = !1, ya = null, ba = !1, xa = Error(a(519));
	function Sa(e) {
		throw Oa(na(Error(a(418, 1 < arguments.length && arguments[1] !== void 0 && arguments[1] ? "text" : "HTML", "")), e)), xa;
	}
	function Ca(e) {
		var t = e.stateNode, n = e.type, r = e.memoizedProps;
		switch (t[It] = e, t[Lt] = r, n) {
			case "dialog":
				I("cancel", t), I("close", t);
				break;
			case "iframe":
			case "object":
			case "embed":
				I("load", t);
				break;
			case "video":
			case "audio":
				for (n = 0; n < tp.length; n++) I(tp[n], t);
				break;
			case "source":
				I("error", t);
				break;
			case "img":
			case "image":
			case "link":
				I("error", t), I("load", t);
				break;
			case "details":
				I("toggle", t);
				break;
			case "input":
				I("invalid", t), yn(t, r.value, r.defaultValue, r.checked, r.defaultChecked, r.type, r.name, !0);
				break;
			case "select":
				I("invalid", t);
				break;
			case "textarea": I("invalid", t), Cn(t, r.value, r.defaultValue, r.children);
		}
		n = r.children, typeof n != "string" && typeof n != "number" && typeof n != "bigint" || t.textContent === "" + n || !0 === r.suppressHydrationWarning || gp(t.textContent, n) ? (r.popover != null && (I("beforetoggle", t), I("toggle", t)), r.onScroll != null && I("scroll", t), r.onScrollEnd != null && I("scrollend", t), r.onClick != null && (t.onclick = Mn), t = !0) : t = !1, t || Sa(e, !0);
	}
	function wa(e) {
		for (_a = e.return; _a;) switch (_a.tag) {
			case 5:
			case 31:
			case 13:
				ba = !1;
				return;
			case 27:
			case 3:
				ba = !0;
				return;
			default: _a = _a.return;
		}
	}
	function Ta(e) {
		if (e !== _a) return !1;
		if (!E) return wa(e), E = !0, !1;
		var t = e.tag, n;
		if ((n = t !== 3 && t !== 27) && ((n = t === 5) && (n = e.type, n = n === "form" || n === "button" || Ap(e.type, e.memoizedProps)), n = !n), n && va && Sa(e), wa(e), t === 13) {
			if (e = e.memoizedState, e = e === null ? null : e.dehydrated, !e) throw Error(a(317));
			va = L(e);
		} else if (t === 31) {
			if (e = e.memoizedState, e = e === null ? null : e.dehydrated, !e) throw Error(a(317));
			va = L(e);
		} else t === 27 ? (t = va, zp(e.type) ? (e = Dm, Dm = null, va = e) : va = t) : va = _a ? Em(e.stateNode.nextSibling) : null;
		return !0;
	}
	function Ea() {
		va = _a = null, E = !1;
	}
	function Da() {
		var e = ya;
		return e !== null && (Od === null ? Od = e : Od.push.apply(Od, e), ya = null), e;
	}
	function Oa(e) {
		ya === null ? ya = [e] : ya.push(e);
	}
	var ka = Me(null), Aa = null, ja = null;
	function Ma(e, t, n) {
		Pe(ka, t._currentValue), t._currentValue = n;
	}
	function Na(e) {
		e._currentValue = ka.current, Ne(ka);
	}
	function Pa(e, t, n) {
		for (; e !== null;) {
			var r = e.alternate;
			if ((e.childLanes & t) === t ? r !== null && (r.childLanes & t) !== t && (r.childLanes |= t) : (e.childLanes |= t, r !== null && (r.childLanes |= t)), e === n) break;
			e = e.return;
		}
	}
	function Fa(e, t, n, r) {
		var i = e.child;
		for (i !== null && (i.return = e); i !== null;) {
			var o = i.dependencies;
			if (o !== null) {
				var s = i.child;
				o = o.firstContext;
				a: for (; o !== null;) {
					var c = o;
					o = i;
					for (var l = 0; l < t.length; l++) if (c.context === t[l]) {
						o.lanes |= n, c = o.alternate, c !== null && (c.lanes |= n), Pa(o.return, n, e), r || (s = null);
						break a;
					}
					o = c.next;
				}
			} else if (i.tag === 18) {
				if (s = i.return, s === null) throw Error(a(341));
				s.lanes |= n, o = s.alternate, o !== null && (o.lanes |= n), Pa(s, n, e), s = null;
			} else i.tag === 13 && i.memoizedState !== null && i.memoizedState.dehydrated === null ? (i.lanes |= n, s = i.alternate, s !== null && (s.lanes |= n), Pa(i.return, n, e), s = i.child, s = s === null ? null : s.sibling) : s = i.child;
			if (s !== null) s.return = i;
			else for (s = i; s !== null;) {
				if (s === e) {
					s = null;
					break;
				}
				if (i = s.sibling, i !== null) {
					i.return = s.return, s = i;
					break;
				}
				s = s.return;
			}
			i = s;
		}
	}
	function Ia(e, t, n, r) {
		e = null;
		for (var i = t, o = !1; i !== null;) {
			if (!o) {
				if (i.flags & 524288) o = !0;
				else if (i.flags & 262144) break;
			}
			if (i.tag === 10) {
				var s = i.alternate;
				if (s === null) throw Error(a(387));
				if (s = s.memoizedProps, s !== null) {
					var c = i.type;
					ti(i.pendingProps.value, s.value) || (e === null ? e = [c] : e.push(c));
				}
			} else if (i === w.current) {
				if (s = i.alternate, s === null) throw Error(a(387));
				s.memoizedState.memoizedState !== i.memoizedState.memoizedState && (e === null ? e = [xh] : e.push(xh));
			}
			i = i.return;
		}
		return e !== null && Fa(t, e, n, r), t.flags |= 262144, e !== null;
	}
	function La(e) {
		for (e = e.firstContext; e !== null;) {
			if (!ti(e.context._currentValue, e.memoizedValue)) return !0;
			e = e.next;
		}
		return !1;
	}
	function Ra(e) {
		Aa = e, ja = null, e = e.dependencies, e !== null && (e.firstContext = null);
	}
	function za(e) {
		return Va(Aa, e);
	}
	function Ba(e, t) {
		return Aa === null && Ra(e), Va(e, t);
	}
	function Va(e, t) {
		var n = t._currentValue;
		if (t = {
			context: t,
			memoizedValue: n,
			next: null
		}, ja === null) {
			if (e === null) throw Error(a(308));
			ja = t, e.dependencies = {
				lanes: 0,
				firstContext: t
			}, e.flags |= 524288;
		} else ja = ja.next = t;
		return n;
	}
	var Ha = typeof AbortController < "u" ? AbortController : function() {
		var e = [], t = this.signal = {
			aborted: !1,
			addEventListener: function(t, n) {
				e.push(n);
			}
		};
		this.abort = function() {
			t.aborted = !0, e.forEach(function(e) {
				return e();
			});
		};
	}, Ua = n.unstable_scheduleCallback, Wa = n.unstable_NormalPriority, D = {
		$$typeof: pe,
		Consumer: null,
		Provider: null,
		_currentValue: null,
		_currentValue2: null,
		_threadCount: 0
	};
	function O() {
		return {
			controller: new Ha(),
			data: /* @__PURE__ */ new Map(),
			refCount: 0
		};
	}
	function Ga(e) {
		e.refCount--, e.refCount === 0 && Ua(Wa, function() {
			e.controller.abort();
		});
	}
	function Ka(e, t) {
		if (e.pendingLanes & 4194048) {
			var n = e.transitionTypes;
			for (n === null && (n = e.transitionTypes = []), e = 0; e < t.length; e++) {
				var r = t[e];
				n.indexOf(r) === -1 && n.push(r);
			}
		}
	}
	var qa = null;
	function Ja(e) {
		var t = e.transitionTypes;
		return e.transitionTypes = null, t;
	}
	var Ya = null, Xa = 0, Za = 0, Qa = null;
	function $a(e, t) {
		if (Ya === null) {
			var n = Ya = [];
			Xa = 0, Za = Xf(), Qa = {
				status: "pending",
				value: void 0,
				then: function(e) {
					n.push(e);
				}
			};
		}
		return Xa++, t.then(eo, eo), t;
	}
	function eo() {
		if (--Xa === 0 && (qa = null, Ya !== null)) {
			Qa !== null && (Qa.status = "fulfilled");
			var e = Ya;
			Ya = null, Za = 0, Qa = null;
			for (var t = 0; t < e.length; t++) (0, e[t])();
		}
	}
	function to(e, t) {
		var n = [], r = {
			status: "pending",
			value: null,
			reason: null,
			then: function(e) {
				n.push(e);
			}
		};
		return e.then(function() {
			r.status = "fulfilled", r.value = t;
			for (var e = 0; e < n.length; e++) (0, n[e])(t);
		}, function(e) {
			for (r.status = "rejected", r.reason = e, e = 0; e < n.length; e++) (0, n[e])(void 0);
		}), r;
	}
	var no = C.S;
	C.S = function(e, t) {
		if (jd = et(), typeof t == "object" && t && typeof t.then == "function" && $a(e, t), qa !== null) for (var n = If; n !== null;) Ka(n, qa), n = n.next;
		if (n = e.types, n !== null) {
			for (var r = If; r !== null;) Ka(r, n), r = r.next;
			if (Za !== 0) {
				r = qa, r === null && (r = qa = []);
				for (var i = 0; i < n.length; i++) {
					var a = n[i];
					r.indexOf(a) === -1 && r.push(a);
				}
			}
		}
		no !== null && no(e, t);
	};
	var k = Me(null);
	function ro() {
		var e = k.current;
		return e === null ? hd.pooledCache : e;
	}
	function io(e, t) {
		t === null ? Pe(k, k.current) : Pe(k, t.pool);
	}
	function ao() {
		var e = ro();
		return e === null ? null : {
			parent: D._currentValue,
			pool: e
		};
	}
	var oo = Error(a(460)), so = Error(a(474)), co = Error(a(542)), lo = { then: function() {} };
	function uo(e) {
		return e = e.status, e === "fulfilled" || e === "rejected";
	}
	function fo(e, t, n) {
		switch (n = e[n], n === void 0 ? e.push(t) : n !== t && (t.then(Mn, Mn), t = n), t.status) {
			case "fulfilled": return t.value;
			case "rejected": throw e = t.reason, go(e), e === void 0 && !("reason" in t) ? Error(a(600)) : e;
			default:
				if (typeof t.status == "string") t.then(Mn, Mn);
				else {
					if (e = hd, e !== null && 100 < e.shellSuspendCounter) throw Error(a(482));
					e = t, e.status = "pending", e.then(function(e) {
						if (t.status === "pending") {
							var n = t;
							n.status = "fulfilled", n.value = e;
						}
					}, function(e) {
						if (t.status === "pending") {
							var n = t;
							n.status = "rejected", n.reason = e;
						}
					});
				}
				switch (t.status) {
					case "fulfilled": return t.value;
					case "rejected": throw e = t.reason, go(e), e;
				}
				throw mo = t, oo;
		}
	}
	function po(e) {
		try {
			var t = e._init;
			return t(e._payload);
		} catch (e) {
			throw typeof e == "object" && e && typeof e.then == "function" ? (mo = e, oo) : e;
		}
	}
	var mo = null;
	function ho() {
		if (mo === null) throw Error(a(459));
		var e = mo;
		return mo = null, e;
	}
	function go(e) {
		if (e === oo || e === co) throw Error(a(483));
	}
	var _o = null, vo = 0;
	function yo(e) {
		var t = vo;
		return vo += 1, _o === null && (_o = []), fo(_o, e, t);
	}
	function bo(e, t) {
		t = t.props.ref, e.ref = t === void 0 ? null : t;
	}
	function xo(e, t) {
		throw t.$$typeof === oe ? Error(a(525)) : (e = Object.prototype.toString.call(t), Error(a(31, e === "[object Object]" ? "object with keys {" + Object.keys(t).join(", ") + "}" : e)));
	}
	function So(e) {
		function t(t, n) {
			if (e) {
				var r = t.deletions;
				r === null ? (t.deletions = [n], t.flags |= 16) : r.push(n);
			}
		}
		function n(n, r) {
			if (!e) return null;
			for (; r !== null;) t(n, r), r = r.sibling;
			return null;
		}
		function r(e) {
			for (var t = /* @__PURE__ */ new Map(); e !== null;) e.key === null ? t.set(e.index, e) : t.set(e.key, e), e = e.sibling;
			return t;
		}
		function i(e, t) {
			return e = Ji(e, t), e.index = 0, e.sibling = null, e;
		}
		function o(t, n, r) {
			return t.index = r, e ? (r = t.alternate, r === null ? (t.flags |= 134217730, n) : (r = r.index, r < n ? (t.flags |= 2, n) : r)) : (t.flags |= 1048576, n);
		}
		function s(t) {
			return e && t.alternate === null && (t.flags |= 134217730), t;
		}
		function c(e, t, n, r) {
			return t === null || t.tag !== 6 ? (t = Qi(n, e.mode, r), t.return = e, t) : (t = i(t, n), t.return = e, t);
		}
		function l(e, t, n, r) {
			var a = n.type;
			return a === le ? (e = d(e, t, n.props.children, r, n.key), bo(e, n), e) : t !== null && (t.elementType === a || typeof a == "object" && a && a.$$typeof === ve && po(a) === t.type) ? (t = i(t, n.props), bo(t, n), t.return = e, t) : (t = Xi(n.type, n.key, n.props, null, e.mode, r), bo(t, n), t.return = e, t);
		}
		function u(e, t, n, r) {
			return t === null || t.tag !== 4 || t.stateNode.containerInfo !== n.containerInfo || t.stateNode.implementation !== n.implementation ? (t = ea(n, e.mode, r), t.return = e, t) : (t = i(t, n.children || []), t.return = e, t);
		}
		function d(e, t, n, r, a) {
			return t === null || t.tag !== 7 ? (t = Zi(n, e.mode, r, a), t.return = e, t) : (t = i(t, n), t.return = e, t);
		}
		function f(e, t, n) {
			if (typeof t == "string" && t !== "" || typeof t == "number" || typeof t == "bigint") return t = Qi("" + t, e.mode, n), t.return = e, t;
			if (typeof t == "object" && t) {
				switch (t.$$typeof) {
					case se: return n = Xi(t.type, t.key, t.props, null, e.mode, n), bo(n, t), n.return = e, n;
					case ce: return t = ea(t, e.mode, n), t.return = e, t;
					case ve: return t = po(t), f(e, t, n);
				}
				if (De(t) || we(t)) return t = Zi(t, e.mode, n, null), t.return = e, t;
				if (typeof t.then == "function") return f(e, yo(t), n);
				if (t.$$typeof === pe) return f(e, Ba(e, t), n);
				xo(e, t);
			}
			return null;
		}
		function p(e, t, n, r) {
			var i = t === null ? null : t.key;
			if (typeof n == "string" && n !== "" || typeof n == "number" || typeof n == "bigint") return i === null ? c(e, t, "" + n, r) : null;
			if (typeof n == "object" && n) {
				switch (n.$$typeof) {
					case se: return n.key === i ? l(e, t, n, r) : null;
					case ce: return n.key === i ? u(e, t, n, r) : null;
					case ve: return n = po(n), p(e, t, n, r);
				}
				if (De(n) || we(n)) return i === null ? d(e, t, n, r, null) : null;
				if (typeof n.then == "function") return p(e, t, yo(n), r);
				if (n.$$typeof === pe) return p(e, t, Ba(e, n), r);
				xo(e, n);
			}
			return null;
		}
		function m(e, t, n, r, i) {
			if (typeof r == "string" && r !== "" || typeof r == "number" || typeof r == "bigint") return e = e.get(n) || null, c(t, e, "" + r, i);
			if (typeof r == "object" && r) {
				switch (r.$$typeof) {
					case se: return e = e.get(r.key === null ? n : r.key) || null, l(t, e, r, i);
					case ce: return e = e.get(r.key === null ? n : r.key) || null, u(t, e, r, i);
					case ve: return r = po(r), m(e, t, n, r, i);
				}
				if (De(r) || we(r)) return e = e.get(n) || null, d(t, e, r, i, null);
				if (typeof r.then == "function") return m(e, t, n, yo(r), i);
				if (r.$$typeof === pe) return m(e, t, n, Ba(t, r), i);
				xo(t, r);
			}
			return null;
		}
		function h(i, a, s, c) {
			for (var l = null, u = null, d = a, h = a = 0, g = null; d !== null && h < s.length; h++) {
				d.index > h ? (g = d, d = null) : g = d.sibling;
				var _ = p(i, d, s[h], c);
				if (_ === null) {
					d === null && (d = g);
					break;
				}
				e && d && _.alternate === null && t(i, d), a = o(_, a, h), u === null ? l = _ : u.sibling = _, u = _, d = g;
			}
			if (h === s.length) return n(i, d), E && fa(i, h), l;
			if (d === null) {
				for (; h < s.length; h++) d = f(i, s[h], c), d !== null && (a = o(d, a, h), u === null ? l = d : u.sibling = d, u = d);
				return E && fa(i, h), l;
			}
			for (d = r(d); h < s.length; h++) g = m(d, i, h, s[h], c), g !== null && (e && (_ = g.alternate, _ !== null && d.delete(_.key === null ? h : _.key)), a = o(g, a, h), u === null ? l = g : u.sibling = g, u = g);
			return e && d.forEach(function(e) {
				return t(i, e);
			}), E && fa(i, h), l;
		}
		function g(i, s, c, l) {
			if (c == null) throw Error(a(151));
			for (var u = null, d = null, h = s, g = s = 0, _ = null, v = c.next(); h !== null && !v.done; g++, v = c.next()) {
				h.index > g ? (_ = h, h = null) : _ = h.sibling;
				var y = p(i, h, v.value, l);
				if (y === null) {
					h === null && (h = _);
					break;
				}
				e && h && y.alternate === null && t(i, h), s = o(y, s, g), d === null ? u = y : d.sibling = y, d = y, h = _;
			}
			if (v.done) return n(i, h), E && fa(i, g), u;
			if (h === null) {
				for (; !v.done; g++, v = c.next()) v = f(i, v.value, l), v !== null && (s = o(v, s, g), d === null ? u = v : d.sibling = v, d = v);
				return E && fa(i, g), u;
			}
			for (h = r(h); !v.done; g++, v = c.next()) v = m(h, i, g, v.value, l), v !== null && (e && (_ = v.alternate, _ !== null && h.delete(_.key === null ? g : _.key)), s = o(v, s, g), d === null ? u = v : d.sibling = v, d = v);
			return e && h.forEach(function(e) {
				return t(i, e);
			}), E && fa(i, g), u;
		}
		function _(e, r, o, c) {
			if (typeof o == "object" && o && o.type === le && o.key === null && o.props.ref === void 0 && (o = o.props.children), typeof o == "object" && o) {
				switch (o.$$typeof) {
					case se:
						a: {
							for (var l = o.key; r !== null;) {
								if (r.key === l) {
									if (l = o.type, l === le) {
										if (r.tag === 7) {
											n(e, r.sibling), c = i(r, o.props.children), bo(c, o), c.return = e, e = c;
											break a;
										}
									} else if (r.elementType === l || typeof l == "object" && l && l.$$typeof === ve && po(l) === r.type) {
										n(e, r.sibling), c = i(r, o.props), bo(c, o), c.return = e, e = c;
										break a;
									}
									n(e, r);
									break;
								}
								t(e, r), r = r.sibling;
							}
							o.type === le ? (c = Zi(o.props.children, e.mode, c, o.key), bo(c, o), c.return = e, e = c) : (c = Xi(o.type, o.key, o.props, null, e.mode, c), bo(c, o), c.return = e, e = c);
						}
						return s(e);
					case ce:
						a: {
							for (l = o.key; r !== null;) {
								if (r.key === l) {
									if (r.tag === 4 && r.stateNode.containerInfo === o.containerInfo && r.stateNode.implementation === o.implementation) {
										n(e, r.sibling), c = i(r, o.children || []), c.return = e, e = c;
										break a;
									}
									n(e, r);
									break;
								}
								t(e, r), r = r.sibling;
							}
							c = ea(o, e.mode, c), c.return = e, e = c;
						}
						return s(e);
					case ve: return o = po(o), _(e, r, o, c);
				}
				if (De(o)) return h(e, r, o, c);
				if (we(o)) {
					if (l = we(o), typeof l != "function") throw Error(a(150));
					return o = l.call(o), g(e, r, o, c);
				}
				if (typeof o.then == "function") return _(e, r, yo(o), c);
				if (o.$$typeof === pe) return _(e, r, Ba(e, o), c);
				xo(e, o);
			}
			return typeof o == "string" && o !== "" || typeof o == "number" || typeof o == "bigint" ? (o = "" + o, r !== null && r.tag === 6 ? (n(e, r.sibling), c = i(r, o), c.return = e, e = c) : (n(e, r), c = Qi(o, e.mode, c), c.return = e, e = c), s(e)) : n(e, r);
		}
		return function(e, t, n, r) {
			try {
				vo = 0;
				var i = _(e, t, n, r);
				return _o = null, i;
			} catch (t) {
				if (t === oo || t === co) throw t;
				var a = Ki(29, t, null, e.mode);
				return a.lanes = r, a.return = e, a;
			}
		};
	}
	var Co = So(!0), wo = So(!1), To = !1;
	function Eo(e) {
		e.updateQueue = {
			baseState: e.memoizedState,
			firstBaseUpdate: null,
			lastBaseUpdate: null,
			shared: {
				pending: null,
				lanes: 0,
				hiddenCallbacks: null
			},
			callbacks: null
		};
	}
	function Do(e, t) {
		e = e.updateQueue, t.updateQueue === e && (t.updateQueue = {
			baseState: e.baseState,
			firstBaseUpdate: e.firstBaseUpdate,
			lastBaseUpdate: e.lastBaseUpdate,
			shared: e.shared,
			callbacks: null
		});
	}
	function Oo(e) {
		return {
			lane: e,
			tag: 0,
			payload: null,
			callback: null,
			next: null
		};
	}
	function ko(e, t, n) {
		var r = e.updateQueue;
		if (r === null) return null;
		if (r = r.shared, md & 2) {
			var i = r.pending;
			return i === null ? t.next = t : (t.next = i.next, i.next = t), r.pending = t, t = Ui(e), Hi(e, null, n), t;
		}
		return zi(e, r, t, n), Ui(e);
	}
	function Ao(e, t, n) {
		if (t = t.updateQueue, t !== null && (t = t.shared, n & 4194048)) {
			var r = t.lanes;
			r &= e.pendingLanes, n |= r, t.lanes = n, kt(e, n);
		}
	}
	function jo(e, t) {
		var n = e.updateQueue, r = e.alternate;
		if (r !== null && (r = r.updateQueue, n === r)) {
			var i = null, a = null;
			if (n = n.firstBaseUpdate, n !== null) {
				do {
					var o = {
						lane: n.lane,
						tag: n.tag,
						payload: n.payload,
						callback: null,
						next: null
					};
					a === null ? i = a = o : a = a.next = o, n = n.next;
				} while (n !== null);
				a === null ? i = a = t : a = a.next = t;
			} else i = a = t;
			n = {
				baseState: r.baseState,
				firstBaseUpdate: i,
				lastBaseUpdate: a,
				shared: r.shared,
				callbacks: r.callbacks
			}, e.updateQueue = n;
		} else e = n.lastBaseUpdate, e === null ? n.firstBaseUpdate = t : e.next = t, n.lastBaseUpdate = t;
	}
	var Mo = !1;
	function No() {
		if (Mo) {
			var e = Qa;
			if (e !== null) throw e;
		}
	}
	function Po(e, t, n, r) {
		Mo = !1;
		var i = e.updateQueue;
		To = !1;
		var a = i.firstBaseUpdate, o = i.lastBaseUpdate, s = i.shared.pending;
		if (s !== null) {
			i.shared.pending = null;
			var c = s, l = c.next;
			c.next = null, o === null ? a = l : o.next = l, o = c;
			var u = e.alternate;
			u !== null && (u = u.updateQueue, s = u.lastBaseUpdate, s !== o && (s === null ? u.firstBaseUpdate = l : s.next = l, u.lastBaseUpdate = c));
		}
		if (a !== null) {
			var d = i.baseState;
			o = 0, u = l = c = null, s = a;
			do {
				var f = s.lane & -536870913, p = f !== s.lane;
				if (p ? (N & f) === f : (r & f) === f) {
					f !== 0 && f === Za && (Mo = !0), u !== null && (u = u.next = {
						lane: 0,
						tag: s.tag,
						payload: s.payload,
						callback: null,
						next: null
					});
					a: {
						var m = e, h = s;
						f = t;
						var g = n;
						switch (h.tag) {
							case 1:
								if (m = h.payload, typeof m == "function") {
									d = m.call(g, d, f);
									break a;
								}
								d = m;
								break a;
							case 3: m.flags = m.flags & -65537 | 128;
							case 0:
								if (m = h.payload, f = typeof m == "function" ? m.call(g, d, f) : m, f == null) break a;
								d = x({}, d, f);
								break a;
							case 2: To = !0;
						}
					}
					f = s.callback, f !== null && (e.flags |= 64, p && (e.flags |= 8192), p = i.callbacks, p === null ? i.callbacks = [f] : p.push(f));
				} else p = {
					lane: f,
					tag: s.tag,
					payload: s.payload,
					callback: s.callback,
					next: null
				}, u === null ? (l = u = p, c = d) : u = u.next = p, o |= f;
				if (s = s.next, s === null) {
					if (s = i.shared.pending, s === null) break;
					p = s, s = p.next, p.next = null, i.lastBaseUpdate = p, i.shared.pending = null;
				}
			} while (1);
			u === null && (c = d), i.baseState = c, i.firstBaseUpdate = l, i.lastBaseUpdate = u, a === null && (i.shared.lanes = 0), Sd |= o, e.lanes = o, e.memoizedState = d;
		}
	}
	function Fo(e, t) {
		if (typeof e != "function") throw Error(a(191, e));
		e.call(t);
	}
	function Io(e, t) {
		var n = e.callbacks;
		if (n !== null) for (e.callbacks = null, e = 0; e < n.length; e++) Fo(n[e], t);
	}
	var Lo = Me(null), Ro = Me(0);
	function zo(e, t) {
		e = bd, Pe(Ro, e), Pe(Lo, t), bd = e | t.baseLanes;
	}
	function Bo() {
		Pe(Ro, bd), Pe(Lo, Lo.current);
	}
	function Vo() {
		bd = Ro.current, Ne(Lo), Ne(Ro);
	}
	var Ho = Me(null), Uo = null;
	function Wo(e) {
		var t = e.alternate;
		Pe(Yo, Yo.current & 1), Pe(Ho, e), Uo === null && (t === null || Lo.current !== null || t.memoizedState !== null) && (Uo = e);
	}
	function Go(e) {
		Pe(Yo, Yo.current), Pe(Ho, e), Uo === null && (Uo = e);
	}
	function Ko(e) {
		e.tag === 22 ? (Pe(Yo, Yo.current), Pe(Ho, e), Uo === null && (Uo = e)) : qo();
	}
	function qo() {
		Pe(Yo, Yo.current), Pe(Ho, Ho.current);
	}
	function Jo(e) {
		Ne(Ho), Uo === e && (Uo = null), Ne(Yo);
	}
	var Yo = Me(0);
	function Xo(e, t) {
		Pe(Ho, Ho.current), Pe(Yo, t);
	}
	function Zo(e) {
		Ne(Yo), Ne(Ho), Uo === e && (Uo = null);
	}
	function Qo(e) {
		for (var t = e; t !== null;) {
			if (t.tag === 13) {
				var n = t.memoizedState;
				if (n !== null && (n = n.dehydrated, n === null || Cm(n) || wm(n))) return t;
			} else if (t.tag === 19 && t.memoizedProps.revealOrder !== "independent") {
				if (t.flags & 128) return t;
			} else if (t.child !== null) {
				t.child.return = t, t = t.child;
				continue;
			}
			if (t === e) break;
			for (; t.sibling === null;) {
				if (t.return === null || t.return === e) return null;
				t = t.return;
			}
			t.sibling.return = t.return, t = t.sibling;
		}
		return null;
	}
	var $o = 0, A = null, es = null, ts = null, ns = !1, rs = !1, is = !1, as = 0, os = 0, ss = null, cs = 0;
	function ls() {
		throw Error(a(321));
	}
	function us(e, t) {
		if (t === null) return !1;
		for (var n = 0; n < t.length && n < e.length; n++) if (!ti(e[n], t[n])) return !1;
		return !0;
	}
	function ds(e, t, n, r, i, a) {
		return $o = a, A = t, t.memoizedState = null, t.updateQueue = null, t.lanes = 0, C.H = e === null || e.memoizedState === null ? Oc : kc, is = !1, a = n(r, i), is = !1, rs && (a = ps(t, n, r, i)), fs(e), a;
	}
	function fs(e) {
		C.H = Dc;
		var t = es !== null && es.next !== null;
		if ($o = 0, ts = es = A = null, ns = !1, os = 0, ss = null, t) throw Error(a(300));
		e === null || Kc || (e = e.dependencies, e !== null && La(e) && (Kc = !0));
	}
	function ps(e, t, n, r) {
		A = e;
		var i = 0;
		do {
			if (rs && (ss = null), os = 0, rs = !1, 25 <= i) throw Error(a(301));
			if (i += 1, ts = es = null, e.updateQueue != null) {
				var o = e.updateQueue;
				o.lastEffect = null, o.events = null, o.stores = null, o.memoCache != null && (o.memoCache.index = 0);
			}
			C.H = Ac, o = t(n, r);
		} while (rs);
		return o;
	}
	function ms() {
		var e = C.H, t = e.useState()[0];
		return t = typeof t.then == "function" ? xs(t) : t, e = e.useState()[0], (es === null ? null : es.memoizedState) !== e && (A.flags |= 1024), t;
	}
	function hs() {
		var e = as !== 0;
		return as = 0, e;
	}
	function gs(e, t, n) {
		t.updateQueue = e.updateQueue, t.flags &= -2053, e.lanes &= ~n;
	}
	function _s(e) {
		if (ns) {
			for (e = e.memoizedState; e !== null;) {
				var t = e.queue;
				t !== null && (t.pending = null), e = e.next;
			}
			ns = !1;
		}
		$o = 0, ts = es = A = null, rs = !1, os = as = 0, ss = null;
	}
	function vs() {
		var e = {
			memoizedState: null,
			baseState: null,
			baseQueue: null,
			queue: null,
			next: null
		};
		return ts === null ? A.memoizedState = ts = e : ts = ts.next = e, ts;
	}
	function ys() {
		if (es === null) {
			var e = A.alternate;
			e = e === null ? null : e.memoizedState;
		} else e = es.next;
		var t = ts === null ? A.memoizedState : ts.next;
		if (t !== null) ts = t, es = e;
		else {
			if (e === null) throw A.alternate === null ? Error(a(467)) : Error(a(310));
			es = e, e = {
				memoizedState: es.memoizedState,
				baseState: es.baseState,
				baseQueue: es.baseQueue,
				queue: es.queue,
				next: null
			}, ts === null ? A.memoizedState = ts = e : ts = ts.next = e;
		}
		return ts;
	}
	function bs() {
		return {
			lastEffect: null,
			events: null,
			stores: null,
			memoCache: null
		};
	}
	function xs(e) {
		var t = os;
		return os += 1, ss === null && (ss = []), e = fo(ss, e, t), t = A, (ts === null ? t.memoizedState : ts.next) === null && (t = t.alternate, C.H = t === null || t.memoizedState === null ? Oc : kc), e;
	}
	function Ss(e) {
		if (typeof e == "object" && e) {
			if (typeof e.then == "function") return xs(e);
			if (e.$$typeof === Se) return;
			if (e.$$typeof === pe) return za(e);
		}
		throw Error(a(438, String(e)));
	}
	function Cs(e) {
		var t = null, n = A.updateQueue;
		if (n !== null && (t = n.memoCache), t == null) {
			var r = A.alternate;
			r !== null && (r = r.updateQueue, r !== null && (r = r.memoCache, r != null && (t = {
				data: r.data.map(function(e) {
					return e.slice();
				}),
				index: 0
			})));
		}
		if (t ??= {
			data: [],
			index: 0
		}, n === null && (n = bs(), A.updateQueue = n), n.memoCache = t, n = t.data[t.index], n === void 0) for (n = t.data[t.index] = Array(e), r = 0; r < e; r++) n[r] = be;
		return t.index++, n;
	}
	function ws(e, t) {
		return typeof t == "function" ? t(e) : t;
	}
	function Ts(e) {
		return Es(ys(), es, e);
	}
	function Es(e, t, n) {
		var r = e.queue;
		if (r === null) throw Error(a(311));
		r.lastRenderedReducer = n;
		var i = e.baseQueue, o = r.pending;
		if (o !== null) {
			if (i !== null) {
				var s = i.next;
				i.next = o.next, o.next = s;
			}
			t.baseQueue = i = o, r.pending = null;
		}
		if (o = e.baseState, i === null) e.memoizedState = o;
		else {
			t = i.next;
			var c = s = null, l = null, u = t, d = !1;
			do {
				var f = u.lane & -536870913;
				if (f === u.lane ? ($o & f) === f : (N & f) === f) {
					var p = u.revertLane;
					if (p === 0) l !== null && (l = l.next = {
						lane: 0,
						revertLane: 0,
						gesture: null,
						action: u.action,
						hasEagerState: u.hasEagerState,
						eagerState: u.eagerState,
						next: null
					}), f === Za && (d = !0);
					else if (($o & p) === p) {
						u = u.next, p === Za && (d = !0);
						continue;
					} else f = {
						lane: 0,
						revertLane: u.revertLane,
						gesture: null,
						action: u.action,
						hasEagerState: u.hasEagerState,
						eagerState: u.eagerState,
						next: null
					}, l === null ? (c = l = f, s = o) : l = l.next = f, A.lanes |= p, Sd |= p;
					f = u.action, is && n(o, f), o = u.hasEagerState ? u.eagerState : n(o, f);
				} else p = {
					lane: f,
					revertLane: u.revertLane,
					gesture: u.gesture,
					action: u.action,
					hasEagerState: u.hasEagerState,
					eagerState: u.eagerState,
					next: null
				}, l === null ? (c = l = p, s = o) : l = l.next = p, A.lanes |= f, Sd |= f;
				u = u.next;
			} while (u !== null && u !== t);
			if (l === null ? s = o : l.next = c, !ti(o, e.memoizedState) && (Kc = !0, d && (n = Qa, n !== null))) throw n;
			e.memoizedState = o, e.baseState = s, e.baseQueue = l, r.lastRenderedState = o;
		}
		return i === null && (r.lanes = 0), [e.memoizedState, r.dispatch];
	}
	function Ds(e) {
		var t = ys(), n = t.queue;
		if (n === null) throw Error(a(311));
		n.lastRenderedReducer = e;
		var r = n.dispatch, i = n.pending, o = t.memoizedState;
		if (i !== null) {
			n.pending = null;
			var s = i = i.next;
			do
				o = e(o, s.action), s = s.next;
			while (s !== i);
			ti(o, t.memoizedState) || (Kc = !0), t.memoizedState = o, t.baseQueue === null && (t.baseState = o), n.lastRenderedState = o;
		}
		return [o, r];
	}
	function Os(e, t, n) {
		var r = A, i = ys(), o = E;
		if (o) {
			if (n === void 0) throw Error(a(407));
			n = n();
		} else n = t();
		var s = !ti((es || i).memoizedState, n);
		if (s && (i.memoizedState = n, Kc = !0), i = i.queue, $s(js.bind(null, r, i, e), [e]), e = i.getSnapshot !== t || s || ts !== null && !!(ts.memoizedState.tag & 1), Js(e ? 9 : 8, { destroy: void 0 }, As.bind(null, r, i, n, t), null), e) {
			if (r.flags |= 2048, hd === null) throw Error(a(349));
			o || $o & 127 || ks(r, t, n);
		}
		return n;
	}
	function ks(e, t, n) {
		e.flags |= 16384, e = {
			getSnapshot: t,
			value: n
		}, t = A.updateQueue, t === null ? (t = bs(), A.updateQueue = t, t.stores = [e]) : (n = t.stores, n === null ? t.stores = [e] : n.push(e));
	}
	function As(e, t, n, r) {
		t.value = n, t.getSnapshot = r, Ms(t) && Ns(e);
	}
	function js(e, t, n) {
		return n(function() {
			Ms(t) && Ns(e);
		});
	}
	function Ms(e) {
		var t = e.getSnapshot;
		e = e.value;
		try {
			var n = t();
			return !ti(e, n);
		} catch {
			return !0;
		}
	}
	function Ns(e) {
		var t = Vi(e, 2);
		t !== null && Yd(t, e, 2);
	}
	function Ps(e) {
		var t = vs();
		if (typeof e == "function") {
			var n = e;
			if (e = n(), is) {
				dt(!0);
				try {
					n();
				} finally {
					dt(!1);
				}
			}
		}
		return t.memoizedState = t.baseState = e, t.queue = {
			pending: null,
			lanes: 0,
			dispatch: null,
			lastRenderedReducer: ws,
			lastRenderedState: e
		}, t;
	}
	function Fs(e, t, n, r) {
		return e.baseState = n, Es(e, es, typeof r == "function" ? r : ws);
	}
	function Is(e, t, n, r, i) {
		if (wc(e)) throw Error(a(485));
		if (e = t.action, e !== null) {
			var o = {
				payload: i,
				action: e,
				next: null,
				isTransition: !0,
				status: "pending",
				value: null,
				reason: null,
				listeners: [],
				then: function(e) {
					o.listeners.push(e);
				}
			};
			C.T === null ? o.isTransition = !1 : n(!0), r(o), n = t.pending, n === null ? (o.next = t.pending = o, Ls(t, o)) : (o.next = n.next, t.pending = n.next = o);
		}
	}
	function Ls(e, t) {
		var n = t.action, r = t.payload, i = e.state;
		if (t.isTransition) {
			var a = C.T, o = {};
			o.types = a === null ? null : a.types, C.T = o;
			try {
				var s = n(i, r), c = C.S;
				c !== null && c(o, s), Rs(e, t, s);
			} catch (n) {
				Bs(e, t, n);
			} finally {
				a !== null && o.types !== null && (a.types = o.types), C.T = a;
			}
		} else try {
			a = n(i, r), Rs(e, t, a);
		} catch (n) {
			Bs(e, t, n);
		}
	}
	function Rs(e, t, n) {
		typeof n == "object" && n && typeof n.then == "function" ? n.then(function(n) {
			zs(e, t, n);
		}, function(n) {
			return Bs(e, t, n);
		}) : zs(e, t, n);
	}
	function zs(e, t, n) {
		t.status = "fulfilled", t.value = n, Vs(t), e.state = n, t = e.pending, t !== null && (n = t.next, n === t ? e.pending = null : (n = n.next, t.next = n, Ls(e, n)));
	}
	function Bs(e, t, n) {
		var r = e.pending;
		if (e.pending = null, r !== null) {
			r = r.next;
			do
				t.status = "rejected", t.reason = n, Vs(t), t = t.next;
			while (t !== r);
		}
		e.action = null;
	}
	function Vs(e) {
		e = e.listeners;
		for (var t = 0; t < e.length; t++) (0, e[t])();
	}
	function Hs(e, t) {
		return t;
	}
	function Us(e, t) {
		if (E) {
			var n = hd.formState;
			if (n !== null) {
				a: {
					var r = A;
					if (E) {
						if (va) {
							b: {
								for (var i = va, a = ba; i.nodeType !== 8;) if (!a || (i = Em(i.nextSibling), i === null)) {
									i = null;
									break b;
								}
								a = i.data, i = a === "F!" || a === "F" ? i : null;
							}
							if (i) {
								va = Em(i.nextSibling), r = i.data === "F!";
								break a;
							}
						}
						Sa(r);
					}
					r = !1;
				}
				r && (t = n[0]);
			}
		}
		return n = vs(), n.memoizedState = n.baseState = t, r = {
			pending: null,
			lanes: 0,
			dispatch: null,
			lastRenderedReducer: Hs,
			lastRenderedState: t
		}, n.queue = r, n = xc.bind(null, A, r), r.dispatch = n, r = Ps(!1), a = Cc.bind(null, A, !1, r.queue), r = vs(), i = {
			state: t,
			dispatch: null,
			action: e,
			pending: null
		}, r.queue = i, n = Is.bind(null, A, i, a, n), i.dispatch = n, r.memoizedState = e, [
			t,
			n,
			!1
		];
	}
	function Ws(e) {
		return Gs(ys(), es, e);
	}
	function Gs(e, t, n) {
		if (t = Es(e, t, Hs)[0], e = Ts(ws)[0], typeof t == "object" && t && typeof t.then == "function") try {
			var r = xs(t);
		} catch (e) {
			throw e === oo ? co : e;
		}
		else r = t;
		t = ys();
		var i = t.queue, a = i.dispatch;
		return n !== t.memoizedState && (A.flags |= 2048, Js(9, { destroy: void 0 }, Ks.bind(null, i, n), null)), [
			r,
			a,
			e
		];
	}
	function Ks(e, t) {
		e.action = t;
	}
	function qs(e) {
		var t = ys(), n = es;
		if (n !== null) return Gs(t, n, e);
		ys(), t = t.memoizedState, n = ys();
		var r = n.queue.dispatch;
		return n.memoizedState = e, [
			t,
			r,
			!1
		];
	}
	function Js(e, t, n, r) {
		return e = {
			tag: e,
			create: n,
			deps: r,
			inst: t,
			next: null
		}, t = A.updateQueue, t === null && (t = bs(), A.updateQueue = t), n = t.lastEffect, n === null ? t.lastEffect = e.next = e : (r = n.next, n.next = e, e.next = r, t.lastEffect = e), e;
	}
	function Ys() {
		return ys().memoizedState;
	}
	function Xs(e, t, n, r) {
		var i = vs();
		A.flags |= e, i.memoizedState = Js(1 | t, { destroy: void 0 }, n, r === void 0 ? null : r);
	}
	function Zs(e, t, n, r) {
		var i = ys();
		r = r === void 0 ? null : r;
		var a = i.memoizedState.inst;
		es !== null && r !== null && us(r, es.memoizedState.deps) ? i.memoizedState = Js(t, a, n, r) : (A.flags |= e, i.memoizedState = Js(1 | t, a, n, r));
	}
	function Qs(e, t) {
		Xs(8390656, 8, e, t);
	}
	function $s(e, t) {
		Zs(2048, 8, e, t);
	}
	function ec(e) {
		A.flags |= 4;
		var t = A.updateQueue;
		if (t === null) t = bs(), A.updateQueue = t, t.events = [e];
		else {
			var n = t.events;
			n === null ? t.events = [e] : n.push(e);
		}
	}
	function tc(e) {
		var t = ys().memoizedState;
		return ec({
			ref: t,
			nextImpl: e
		}), function() {
			if (md & 2) throw Error(a(440));
			return t.impl.apply(void 0, arguments);
		};
	}
	function nc(e, t) {
		return Zs(4, 2, e, t);
	}
	function rc(e, t) {
		return Zs(4, 4, e, t);
	}
	function ic(e, t) {
		if (typeof t == "function") {
			e = e();
			var n = t(e);
			return function() {
				typeof n == "function" ? n() : t(null);
			};
		}
		if (t != null) return e = e(), t.current = e, function() {
			t.current = null;
		};
	}
	function ac(e, t, n) {
		n = n == null ? null : n.concat([e]), Zs(4, 4, ic.bind(null, t, e), n);
	}
	function oc() {}
	function sc(e, t) {
		var n = ys();
		t = t === void 0 ? null : t;
		var r = n.memoizedState;
		return t !== null && us(t, r[1]) ? r[0] : (n.memoizedState = [e, t], e);
	}
	function cc(e, t) {
		var n = ys();
		t = t === void 0 ? null : t;
		var r = n.memoizedState;
		if (t !== null && us(t, r[1])) return r[0];
		if (r = e(), is) {
			dt(!0);
			try {
				e();
			} finally {
				dt(!1);
			}
		}
		return n.memoizedState = [r, t], r;
	}
	function lc(e, t, n) {
		return n === void 0 || $o & 1073741824 && !(N & 261930) ? e.memoizedState = t : (e.memoizedState = n, e = qd(), A.lanes |= e, Sd |= e, n);
	}
	function uc(e, t, n, r) {
		return ti(n, t) ? n : Lo.current === null ? !($o & 106) || $o & 1073741824 && !(N & 261930) ? (Kc = !0, e.memoizedState = n) : (e = qd(), A.lanes |= e, Sd |= e, t) : (e = lc(e, n, r), ti(e, t) || (Kc = !0), e);
	}
	function dc(e, t, n, r, i) {
		var a = Oe.p;
		Oe.p = a !== 0 && 8 > a ? a : 8;
		var o = C.T, s = {};
		s.types = o === null ? null : o.types, C.T = s, Cc(e, !1, t, n);
		try {
			var c = i(), l = C.S;
			l !== null && l(s, c), typeof c == "object" && c && typeof c.then == "function" ? Sc(e, t, to(c, r), Kd(e)) : Sc(e, t, r, Kd(e));
		} catch (n) {
			Sc(e, t, {
				then: function() {},
				status: "rejected",
				reason: n
			}, Kd());
		} finally {
			Oe.p = a, o !== null && s.types !== null && (o.types = s.types), C.T = o;
		}
	}
	function fc() {}
	function pc(e, t, n, r) {
		if (e.tag !== 5) throw Error(a(476));
		var i = mc(e).queue;
		dc(e, i, t, ke, n === null ? fc : function() {
			return hc(e), n(r);
		});
	}
	function mc(e) {
		var t = e.memoizedState;
		if (t !== null) return t;
		t = {
			memoizedState: ke,
			baseState: ke,
			baseQueue: null,
			queue: {
				pending: null,
				lanes: 0,
				dispatch: null,
				lastRenderedReducer: ws,
				lastRenderedState: ke
			},
			next: null
		};
		var n = {};
		return t.next = {
			memoizedState: n,
			baseState: n,
			baseQueue: null,
			queue: {
				pending: null,
				lanes: 0,
				dispatch: null,
				lastRenderedReducer: ws,
				lastRenderedState: n
			},
			next: null
		}, e.memoizedState = t, e = e.alternate, e !== null && (e.memoizedState = t), t;
	}
	function hc(e) {
		var t = mc(e);
		t.next === null && (t = e.alternate.memoizedState), Sc(e, t.next.queue, {}, Kd());
	}
	function gc() {
		return za(xh);
	}
	function _c() {
		return ys().memoizedState;
	}
	function vc() {
		return ys().memoizedState;
	}
	function yc(e) {
		for (var t = e.return; t !== null;) {
			switch (t.tag) {
				case 24:
				case 3:
					var n = Kd();
					e = Oo(n);
					var r = ko(t, e, n);
					r !== null && (Yd(r, t, n), Ao(r, t, n)), t = { cache: O() }, e.payload = t;
					return;
			}
			t = t.return;
		}
	}
	function bc(e, t, n) {
		var r = Kd();
		n = {
			lane: r,
			revertLane: 0,
			gesture: null,
			action: n,
			hasEagerState: !1,
			eagerState: null,
			next: null
		}, wc(e) ? Tc(t, n) : (n = Bi(e, t, n, r), n !== null && (Yd(n, e, r), Ec(n, t, r)));
	}
	function xc(e, t, n) {
		Sc(e, t, n, Kd());
	}
	function Sc(e, t, n, r) {
		var i = {
			lane: r,
			revertLane: 0,
			gesture: null,
			action: n,
			hasEagerState: !1,
			eagerState: null,
			next: null
		};
		if (wc(e)) Tc(t, i);
		else {
			var a = e.alternate;
			if (e.lanes === 0 && (a === null || a.lanes === 0) && (a = t.lastRenderedReducer, a !== null)) try {
				var o = t.lastRenderedState, s = a(o, n);
				if (i.hasEagerState = !0, i.eagerState = s, ti(s, o)) return zi(e, t, i, 0), hd === null && Ri(), !1;
			} catch {}
			if (n = Bi(e, t, i, r), n !== null) return Yd(n, e, r), Ec(n, t, r), !0;
		}
		return !1;
	}
	function Cc(e, t, n, r) {
		if (r = {
			lane: 2,
			revertLane: Xf(),
			gesture: null,
			action: r,
			hasEagerState: !1,
			eagerState: null,
			next: null
		}, wc(e)) {
			if (t) throw Error(a(479));
		} else t = Bi(e, n, r, 2), t !== null && Yd(t, e, 2);
	}
	function wc(e) {
		var t = e.alternate;
		return e === A || t !== null && t === A;
	}
	function Tc(e, t) {
		rs = ns = !0;
		var n = e.pending;
		n === null ? t.next = t : (t.next = n.next, n.next = t), e.pending = t;
	}
	function Ec(e, t, n) {
		if (n & 4194048) {
			var r = t.lanes;
			r &= e.pendingLanes, n |= r, t.lanes = n, kt(e, n);
		}
	}
	var Dc = {
		readContext: za,
		use: Ss,
		useCallback: ls,
		useContext: ls,
		useEffect: ls,
		useImperativeHandle: ls,
		useLayoutEffect: ls,
		useInsertionEffect: ls,
		useMemo: ls,
		useReducer: ls,
		useRef: ls,
		useState: ls,
		useDebugValue: ls,
		useDeferredValue: ls,
		useTransition: ls,
		useSyncExternalStore: ls,
		useId: ls,
		useHostTransitionStatus: ls,
		useFormState: ls,
		useActionState: ls,
		useOptimistic: ls,
		useMemoCache: ls,
		useCacheRefresh: ls,
		useEffectEvent: ls
	}, Oc = {
		readContext: za,
		use: Ss,
		useCallback: function(e, t) {
			return vs().memoizedState = [e, t === void 0 ? null : t], e;
		},
		useContext: za,
		useEffect: Qs,
		useImperativeHandle: function(e, t, n) {
			n = n == null ? null : n.concat([e]), Xs(4194308, 4, ic.bind(null, t, e), n);
		},
		useLayoutEffect: function(e, t) {
			return Xs(4194308, 4, e, t);
		},
		useInsertionEffect: function(e, t) {
			Xs(4, 2, e, t);
		},
		useMemo: function(e, t) {
			var n = vs();
			t = t === void 0 ? null : t;
			var r = e();
			if (is) {
				dt(!0);
				try {
					e();
				} finally {
					dt(!1);
				}
			}
			return n.memoizedState = [r, t], r;
		},
		useReducer: function(e, t, n) {
			var r = vs();
			if (n !== void 0) {
				var i = n(t);
				if (is) {
					dt(!0);
					try {
						n(t);
					} finally {
						dt(!1);
					}
				}
			} else i = t;
			return r.memoizedState = r.baseState = i, e = {
				pending: null,
				lanes: 0,
				dispatch: null,
				lastRenderedReducer: e,
				lastRenderedState: i
			}, r.queue = e, e = e.dispatch = bc.bind(null, A, e), [r.memoizedState, e];
		},
		useRef: function(e) {
			var t = vs();
			return e = { current: e }, t.memoizedState = e;
		},
		useState: function(e) {
			e = Ps(e);
			var t = e.queue, n = xc.bind(null, A, t);
			return t.dispatch = n, [e.memoizedState, n];
		},
		useDebugValue: oc,
		useDeferredValue: function(e, t) {
			return lc(vs(), e, t);
		},
		useTransition: function() {
			var e = Ps(!1);
			return e = dc.bind(null, A, e.queue, !0, !1), vs().memoizedState = e, [!1, e];
		},
		useSyncExternalStore: function(e, t, n) {
			var r = A, i = vs();
			if (E) {
				if (n === void 0) throw Error(a(407));
				n = n();
			} else {
				if (n = t(), hd === null) throw Error(a(349));
				N & 127 || ks(r, t, n);
			}
			i.memoizedState = n;
			var o = {
				value: n,
				getSnapshot: t
			};
			return i.queue = o, Qs(js.bind(null, r, o, e), [e]), r.flags |= 2048, Js(9, { destroy: void 0 }, As.bind(null, r, o, n, t), null), n;
		},
		useId: function() {
			var e = vs(), t = hd.identifierPrefix;
			if (E) {
				var n = da, r = ua;
				n = (r & ~(1 << 32 - ft(r) - 1)).toString(32) + n, t = "_" + t + "R_" + n, n = as++, 0 < n && (t += "H" + n.toString(32)), t += "_";
			} else n = cs++, t = "_" + t + "r_" + n.toString(32) + "_";
			return e.memoizedState = t;
		},
		useHostTransitionStatus: gc,
		useFormState: Us,
		useActionState: Us,
		useOptimistic: function(e) {
			var t = vs();
			t.memoizedState = t.baseState = e;
			var n = {
				pending: null,
				lanes: 0,
				dispatch: null,
				lastRenderedReducer: null,
				lastRenderedState: null
			};
			return t.queue = n, t = Cc.bind(null, A, !0, n), n.dispatch = t, [e, t];
		},
		useMemoCache: Cs,
		useCacheRefresh: function() {
			return vs().memoizedState = yc.bind(null, A);
		},
		useEffectEvent: function(e) {
			var t = vs(), n = { impl: e };
			return t.memoizedState = n, function() {
				if (md & 2) throw Error(a(440));
				return n.impl.apply(void 0, arguments);
			};
		}
	}, kc = {
		readContext: za,
		use: Ss,
		useCallback: sc,
		useContext: za,
		useEffect: $s,
		useImperativeHandle: ac,
		useInsertionEffect: nc,
		useLayoutEffect: rc,
		useMemo: cc,
		useReducer: Ts,
		useRef: Ys,
		useState: function() {
			return Ts(ws);
		},
		useDebugValue: oc,
		useDeferredValue: function(e, t) {
			return uc(ys(), es.memoizedState, e, t);
		},
		useTransition: function() {
			var e = Ts(ws)[0], t = ys().memoizedState;
			return [typeof e == "boolean" ? e : xs(e), t];
		},
		useSyncExternalStore: Os,
		useId: _c,
		useHostTransitionStatus: gc,
		useFormState: Ws,
		useActionState: Ws,
		useOptimistic: function(e, t) {
			return Fs(ys(), es, e, t);
		},
		useMemoCache: Cs,
		useCacheRefresh: vc,
		useEffectEvent: tc
	}, Ac = {
		readContext: za,
		use: Ss,
		useCallback: sc,
		useContext: za,
		useEffect: $s,
		useImperativeHandle: ac,
		useInsertionEffect: nc,
		useLayoutEffect: rc,
		useMemo: cc,
		useReducer: Ds,
		useRef: Ys,
		useState: function() {
			return Ds(ws);
		},
		useDebugValue: oc,
		useDeferredValue: function(e, t) {
			var n = ys();
			return es === null ? lc(n, e, t) : uc(n, es.memoizedState, e, t);
		},
		useTransition: function() {
			var e = Ds(ws)[0], t = ys().memoizedState;
			return [typeof e == "boolean" ? e : xs(e), t];
		},
		useSyncExternalStore: Os,
		useId: _c,
		useHostTransitionStatus: gc,
		useFormState: qs,
		useActionState: qs,
		useOptimistic: function(e, t) {
			var n = ys();
			return es === null ? (n.baseState = e, [e, n.queue.dispatch]) : Fs(n, es, e, t);
		},
		useMemoCache: Cs,
		useCacheRefresh: vc,
		useEffectEvent: tc
	};
	function jc(e, t, n, r) {
		t = e.memoizedState, n = n(r, t), n = n == null ? t : x({}, t, n), e.memoizedState = n, e.lanes === 0 && (e.updateQueue.baseState = n);
	}
	var Mc = {
		enqueueSetState: function(e, t, n) {
			e = e._reactInternals;
			var r = Kd(), i = Oo(r);
			i.payload = t, n != null && (i.callback = n), t = ko(e, i, r), t !== null && (Yd(t, e, r), Ao(t, e, r));
		},
		enqueueReplaceState: function(e, t, n) {
			e = e._reactInternals;
			var r = Kd(), i = Oo(r);
			i.tag = 1, i.payload = t, n != null && (i.callback = n), t = ko(e, i, r), t !== null && (Yd(t, e, r), Ao(t, e, r));
		},
		enqueueForceUpdate: function(e, t) {
			e = e._reactInternals;
			var n = Kd(), r = Oo(n);
			r.tag = 2, t != null && (r.callback = t), t = ko(e, r, n), t !== null && (Yd(t, e, n), Ao(t, e, n));
		}
	};
	function Nc(e, t, n, r, i, a, o) {
		return e = e.stateNode, typeof e.shouldComponentUpdate == "function" ? e.shouldComponentUpdate(r, a, o) : t.prototype && t.prototype.isPureReactComponent ? !ni(n, r) || !ni(i, a) : !0;
	}
	function Pc(e, t, n, r) {
		e = t.state, typeof t.componentWillReceiveProps == "function" && t.componentWillReceiveProps(n, r), typeof t.UNSAFE_componentWillReceiveProps == "function" && t.UNSAFE_componentWillReceiveProps(n, r), t.state !== e && Mc.enqueueReplaceState(t, t.state, null);
	}
	function Fc(e, t) {
		var n = t;
		if ("ref" in t) for (var r in n = {}, t) r !== "ref" && (n[r] = t[r]);
		if (e = e.defaultProps) for (var i in n === t && (n = x({}, n)), e) n[i] === void 0 && (n[i] = e[i]);
		return n;
	}
	function Ic(e) {
		Pi(e);
	}
	function Lc(e) {
		console.error(e);
	}
	function Rc(e) {
		Pi(e);
	}
	function zc(e, t) {
		try {
			var n = e.onUncaughtError;
			n(t.value, { componentStack: t.stack });
		} catch (e) {
			setTimeout(function() {
				throw e;
			});
		}
	}
	function Bc(e, t, n) {
		try {
			var r = e.onCaughtError;
			r(n.value, {
				componentStack: n.stack,
				errorBoundary: t.tag === 1 ? t.stateNode : null
			});
		} catch (e) {
			setTimeout(function() {
				throw e;
			});
		}
	}
	function Vc(e, t, n) {
		return n = Oo(n), n.tag = 3, n.payload = { element: null }, n.callback = function() {
			zc(e, t);
		}, n;
	}
	function Hc(e) {
		return e = Oo(e), e.tag = 3, e;
	}
	function Uc(e, t, n, r) {
		var i = n.type.getDerivedStateFromError;
		if (typeof i == "function") {
			var a = r.value;
			e.payload = function() {
				return i(a);
			}, e.callback = function() {
				Bc(t, n, r);
			};
		}
		var o = n.stateNode;
		o !== null && typeof o.componentDidCatch == "function" && (e.callback = function() {
			Bc(t, n, r), typeof i != "function" && (F === null ? F = /* @__PURE__ */ new Set([this]) : F.add(this));
			var e = r.stack;
			this.componentDidCatch(r.value, { componentStack: e === null ? "" : e });
		});
	}
	function Wc(e, t, n, r, i) {
		if (n.flags |= 32768, typeof r == "object" && r && typeof r.then == "function") {
			if (t = n.alternate, t !== null && Ia(t, n, i, !0), n = Ho.current, n !== null) {
				switch (n.tag) {
					case 31:
					case 13:
					case 19: return Uo === null ? cf() : n.alternate === null && xd === 0 && (xd = 3), n.flags &= -257, n.flags |= 65536, n.lanes = i, r === lo ? n.flags |= 16384 : (t = n.updateQueue, t === null ? n.updateQueue = /* @__PURE__ */ new Set([r]) : t.add(r), Af(e, r, i)), !1;
					case 22: return n.flags |= 65536, r === lo ? n.flags |= 16384 : (t = n.updateQueue, t === null ? (t = {
						transitions: null,
						markerInstances: null,
						retryQueue: /* @__PURE__ */ new Set([r])
					}, n.updateQueue = t) : (n = t.retryQueue, n === null ? t.retryQueue = /* @__PURE__ */ new Set([r]) : n.add(r)), Af(e, r, i)), !1;
				}
				throw Error(a(435, n.tag));
			}
			return Af(e, r, i), cf(), !1;
		}
		if (E) return t = Ho.current, t === null ? (r !== xa && (t = Error(a(423), { cause: r }), Oa(na(t, n))), e = e.current.alternate, e.flags |= 65536, i &= -i, e.lanes |= i, r = na(r, n), i = Vc(e.stateNode, r, i), jo(e, i), xd !== 4 && (xd = 2)) : (!(t.flags & 65536) && (t.flags |= 256), t.flags |= 65536, t.lanes = i, r !== xa && (e = Error(a(422), { cause: r }), Oa(na(e, n)))), !1;
		var o = Error(a(520), { cause: r });
		if (o = na(o, n), Dd === null ? Dd = [o] : Dd.push(o), xd !== 4 && (xd = 2), t === null) return !0;
		r = na(r, n), n = t;
		do {
			switch (n.tag) {
				case 3: return n.flags |= 65536, e = i & -i, n.lanes |= e, e = Vc(n.stateNode, r, e), jo(n, e), !1;
				case 1:
					if (t = n.type, o = n.stateNode, !(n.flags & 128) && (typeof t.getDerivedStateFromError == "function" || o !== null && typeof o.componentDidCatch == "function" && (F === null || !F.has(o)))) return n.flags |= 65536, i &= -i, n.lanes |= i, i = Hc(i), Uc(i, e, n, r), jo(n, i), !1;
					break;
				case 22: if (n.memoizedState !== null) return n.flags |= 65536, !1;
			}
			n = n.return;
		} while (n !== null);
		return !1;
	}
	var Gc = Error(a(461)), Kc = !1;
	function qc(e, t, n, r) {
		t.child = e === null ? wo(t, null, n, r) : Co(t, e.child, n, r);
	}
	function Jc(e, t, n, r, i) {
		n = n.render;
		var a = t.ref;
		if ("ref" in r) {
			var o = {};
			for (var s in r) s !== "ref" && (o[s] = r[s]);
		} else o = r;
		return Ra(t), r = ds(e, t, n, o, a, i), s = hs(), e !== null && !Kc ? (gs(e, t, i), Sl(e, t, i)) : (E && s && ma(t), t.flags |= 1, qc(e, t, r, i), t.child);
	}
	function Yc(e, t, n, r, i) {
		if (e === null) {
			var a = n.type;
			return typeof a == "function" && !qi(a) && a.defaultProps === void 0 && n.compare === null ? (t.tag = 15, t.type = a, Xc(e, t, a, r, i)) : (e = Xi(n.type, null, r, t, t.mode, i), e.ref = t.ref, e.return = t, t.child = e);
		}
		if (a = e.child, !Cl(e, i)) {
			var o = a.memoizedProps;
			if (n = n.compare, n = n === null ? ni : n, n(o, r) && e.ref === t.ref) return Sl(e, t, i);
		}
		return t.flags |= 1, e = Ji(a, r), e.ref = t.ref, e.return = t, t.child = e;
	}
	function Xc(e, t, n, r, i) {
		if (e !== null) {
			var a = e.memoizedProps;
			if (ni(a, r) && e.ref === t.ref) {
				if (Kc = !1, t.pendingProps = r = a, Cl(e, i)) e.flags & 131072 && (Kc = !0);
				else return t.lanes = e.lanes, Sl(e, t, i);
			}
		}
		return il(e, t, n, r, i);
	}
	function Zc(e, t, n, r) {
		var i = r.children, a = e === null ? null : e.memoizedState;
		if (e === null && t.stateNode === null && (t.stateNode = {
			_visibility: 1,
			_pendingMarkers: null,
			_retryCache: null,
			_transitions: null
		}), r.mode === "hidden") {
			if (t.flags & 128) {
				if (a = a === null ? n : a.baseLanes | n, e !== null) {
					for (r = t.child = e.child, i = 0; r !== null;) i = i | r.lanes | r.childLanes, r = r.sibling;
					r = i & ~a;
				} else r = 0, t.child = null;
				return $c(e, t, a, n, r);
			}
			if (n & 536870912) t.memoizedState = {
				baseLanes: 0,
				cachePool: null
			}, e !== null && io(t, a === null ? null : a.cachePool), a === null ? Bo() : zo(t, a), Ko(t);
			else return r = t.lanes = 536870912, $c(e, t, a === null ? n : a.baseLanes | n, n, r);
		} else a === null ? (e !== null && io(t, null), Bo(), qo()) : (io(t, a.cachePool), zo(t, a), qo(), t.memoizedState = null);
		return qc(e, t, i, n), t.child;
	}
	function Qc(e, t) {
		return e !== null && e.tag === 22 || t.stateNode !== null || (t.stateNode = {
			_visibility: 1,
			_pendingMarkers: null,
			_retryCache: null,
			_transitions: null
		}), t.sibling;
	}
	function $c(e, t, n, r, i) {
		var a = ro();
		return a = a === null ? null : {
			parent: D._currentValue,
			pool: a
		}, t.memoizedState = {
			baseLanes: n,
			cachePool: a
		}, e !== null && io(t, null), Bo(), Ko(t), e !== null && Ia(e, t, r, !0), t.childLanes = i, null;
	}
	function el(e, t) {
		return t = pl({
			mode: t.mode,
			children: t.children
		}, e.mode), t.ref = e.ref, e.child = t, t.return = e, t;
	}
	function tl(e, t, n) {
		return Co(t, e.child, null, n), e = el(t, t.pendingProps), e.flags |= 2, Jo(t), t.memoizedState = null, e;
	}
	function nl(e, t, n) {
		var r = t.pendingProps, i = !!(t.flags & 128);
		if (t.flags &= -129, e === null) {
			if (E) {
				if (r.mode === "hidden") return e = el(t, r), t.lanes = 536870912, e.memoizedState = {
					baseLanes: 0,
					cachePool: null
				}, Qc(null, e);
				if (Go(t), (e = va) ? (e = Sm(e, ba), e = e !== null && e.data === "&" ? e : null, e !== null && (t.memoizedState = {
					dehydrated: e,
					treeContext: la === null ? null : {
						id: ua,
						overflow: da
					},
					retryLane: 536870912,
					hydrationErrors: null
				}, n = $i(e), n.return = t, t.child = n, _a = t, va = null)) : e = null, e === null) throw Sa(t);
				return t.lanes = 536870912, null;
			}
			return el(t, r);
		}
		var o = e.memoizedState;
		if (o !== null) {
			var s = o.dehydrated;
			if (Go(t), i) {
				if (t.flags & 256) t.flags &= -257, t = tl(e, t, n);
				else if (t.memoizedState !== null) t.child = e.child, t.flags |= 128, t = null;
				else throw Error(a(558));
			} else if (Kc || Ia(e, t, n, !1), i = (n & e.childLanes) !== 0, Kc || i) {
				if (Lo.current === null) {
					if (r = hd, r !== null && (s = At(r, n), s !== 0 && s !== o.retryLane)) throw o.retryLane = s, Vi(e, s), Yd(r, e, s), Gc;
					cf();
				}
				t = tl(e, t, n);
			} else e = o.treeContext, va = Em(s.nextSibling), _a = t, E = !0, ya = null, ba = !1, e !== null && ga(t, e), t = el(t, r), t.flags |= 134221824;
			return t;
		}
		return e = Ji(e.child, {
			mode: r.mode,
			children: r.children
		}), e.ref = t.ref, t.child = e, e.return = t, e;
	}
	function rl(e, t) {
		var n = t.ref;
		if (n === null) e !== null && e.ref !== null && (t.flags |= 4194816);
		else {
			if (typeof n != "function" && typeof n != "object") throw Error(a(284));
			(e === null || e.ref !== n) && (t.flags |= 4194816);
		}
	}
	function il(e, t, n, r, i) {
		return Ra(t), n = ds(e, t, n, r, void 0, i), r = hs(), e !== null && !Kc ? (gs(e, t, i), Sl(e, t, i)) : (E && r && ma(t), t.flags |= 1, qc(e, t, n, i), t.child);
	}
	function al(e, t, n, r, i, a) {
		return Ra(t), t.updateQueue = null, n = ps(t, r, n, i), fs(e), r = hs(), e !== null && !Kc ? (gs(e, t, a), Sl(e, t, a)) : (E && r && ma(t), t.flags |= 1, qc(e, t, n, a), t.child);
	}
	function ol(e, t, n, r, i) {
		if (Ra(t), t.stateNode === null) {
			var a = Wi, o = n.contextType;
			typeof o == "object" && o && (a = za(o)), a = new n(r, a), t.memoizedState = a.state !== null && a.state !== void 0 ? a.state : null, a.updater = Mc, t.stateNode = a, a._reactInternals = t, a = t.stateNode, a.props = r, a.state = t.memoizedState, a.refs = {}, Eo(t), o = n.contextType, a.context = typeof o == "object" && o ? za(o) : Wi, a.state = t.memoizedState, o = n.getDerivedStateFromProps, typeof o == "function" && (jc(t, n, o, r), a.state = t.memoizedState), typeof n.getDerivedStateFromProps == "function" || typeof a.getSnapshotBeforeUpdate == "function" || typeof a.UNSAFE_componentWillMount != "function" && typeof a.componentWillMount != "function" || (o = a.state, typeof a.componentWillMount == "function" && a.componentWillMount(), typeof a.UNSAFE_componentWillMount == "function" && a.UNSAFE_componentWillMount(), o !== a.state && Mc.enqueueReplaceState(a, a.state, null), Po(t, r, a, i), No(), a.state = t.memoizedState), typeof a.componentDidMount == "function" && (t.flags |= 4194308), r = !0;
		} else if (e === null) {
			a = t.stateNode;
			var s = t.memoizedProps, c = Fc(n, s);
			a.props = c;
			var l = a.context, u = n.contextType;
			o = Wi, typeof u == "object" && u && (o = za(u));
			var d = n.getDerivedStateFromProps;
			u = typeof d == "function" || typeof a.getSnapshotBeforeUpdate == "function", s = t.pendingProps !== s, u || typeof a.UNSAFE_componentWillReceiveProps != "function" && typeof a.componentWillReceiveProps != "function" || (s || l !== o) && Pc(t, a, r, o), To = !1;
			var f = t.memoizedState;
			a.state = f, Po(t, r, a, i), No(), l = t.memoizedState, s || f !== l || To ? (typeof d == "function" && (jc(t, n, d, r), l = t.memoizedState), (c = To || Nc(t, n, c, r, f, l, o)) ? (u || typeof a.UNSAFE_componentWillMount != "function" && typeof a.componentWillMount != "function" || (typeof a.componentWillMount == "function" && a.componentWillMount(), typeof a.UNSAFE_componentWillMount == "function" && a.UNSAFE_componentWillMount()), typeof a.componentDidMount == "function" && (t.flags |= 4194308)) : (typeof a.componentDidMount == "function" && (t.flags |= 4194308), t.memoizedProps = r, t.memoizedState = l), a.props = r, a.state = l, a.context = o, r = c) : (typeof a.componentDidMount == "function" && (t.flags |= 4194308), r = !1);
		} else {
			a = t.stateNode, Do(e, t), o = t.memoizedProps, u = Fc(n, o), a.props = u, d = t.pendingProps, f = a.context, l = n.contextType, c = Wi, typeof l == "object" && l && (c = za(l)), s = n.getDerivedStateFromProps, (l = typeof s == "function" || typeof a.getSnapshotBeforeUpdate == "function") || typeof a.UNSAFE_componentWillReceiveProps != "function" && typeof a.componentWillReceiveProps != "function" || (o !== d || f !== c) && Pc(t, a, r, c), To = !1, f = t.memoizedState, a.state = f, Po(t, r, a, i), No();
			var p = t.memoizedState;
			o !== d || f !== p || To || e !== null && e.dependencies !== null && La(e.dependencies) ? (typeof s == "function" && (jc(t, n, s, r), p = t.memoizedState), (u = To || Nc(t, n, u, r, f, p, c) || e !== null && e.dependencies !== null && La(e.dependencies)) ? (l || typeof a.UNSAFE_componentWillUpdate != "function" && typeof a.componentWillUpdate != "function" || (typeof a.componentWillUpdate == "function" && a.componentWillUpdate(r, p, c), typeof a.UNSAFE_componentWillUpdate == "function" && a.UNSAFE_componentWillUpdate(r, p, c)), typeof a.componentDidUpdate == "function" && (t.flags |= 4), typeof a.getSnapshotBeforeUpdate == "function" && (t.flags |= 1024)) : (typeof a.componentDidUpdate != "function" || o === e.memoizedProps && f === e.memoizedState || (t.flags |= 4), typeof a.getSnapshotBeforeUpdate != "function" || o === e.memoizedProps && f === e.memoizedState || (t.flags |= 1024), t.memoizedProps = r, t.memoizedState = p), a.props = r, a.state = p, a.context = c, r = u) : (typeof a.componentDidUpdate != "function" || o === e.memoizedProps && f === e.memoizedState || (t.flags |= 4), typeof a.getSnapshotBeforeUpdate != "function" || o === e.memoizedProps && f === e.memoizedState || (t.flags |= 1024), r = !1);
		}
		return a = r, rl(e, t), r = !!(t.flags & 128), a || r ? (a = t.stateNode, n = r && typeof n.getDerivedStateFromError != "function" ? null : a.render(), t.flags |= 1, e !== null && r ? (t.child = Co(t, e.child, null, i), t.child = Co(t, null, n, i)) : qc(e, t, n, i), t.memoizedState = a.state, e = t.child) : e = Sl(e, t, i), e;
	}
	function sl(e, t, n, r) {
		return Ea(), t.flags |= 256, qc(e, t, n, r), t.child;
	}
	var cl = {
		dehydrated: null,
		treeContext: null,
		retryLane: 0,
		hydrationErrors: null
	};
	function ll(e) {
		return {
			baseLanes: e,
			cachePool: ao()
		};
	}
	function ul(e, t, n) {
		return e = e === null ? 0 : e.childLanes & ~n, t && (e |= Td), e;
	}
	function dl(e, t, n) {
		var r = t.pendingProps, i = !1, a = !!(t.flags & 128), o;
		if ((o = a) || (o = e !== null && e.memoizedState === null ? !1 : !!(Yo.current & 2)), o && (i = !0, t.flags &= -129), o = !!(t.flags & 32), t.flags &= -33, e === null) {
			if (E) {
				if (i ? Wo(t) : qo(), (e = va) ? (e = Sm(e, ba), e = e !== null && e.data !== "&" ? e : null, e !== null && (t.memoizedState = {
					dehydrated: e,
					treeContext: la === null ? null : {
						id: ua,
						overflow: da
					},
					retryLane: 536870912,
					hydrationErrors: null
				}, n = $i(e), n.return = t, t.child = n, _a = t, va = null)) : e = null, e === null) throw Sa(t);
				return t.lanes = wm(e) ? 32 : 536870912, null;
			}
			return a = r.children, r = r.fallback, i ? (qo(), i = t.mode, a = pl({
				mode: "hidden",
				children: a
			}, i), r = Zi(r, i, n, null), a.return = t, r.return = t, a.sibling = r, t.child = a, r = t.child, r.memoizedState = ll(n), r.childLanes = ul(e, o, n), t.memoizedState = cl, Qc(null, r)) : (Wo(t), fl(t, a));
		}
		var s = e.memoizedState;
		if (s !== null) {
			var c = s.dehydrated;
			if (c !== null) return hl(e, t, a, o, r, c, s, n);
		}
		return i ? (qo(), i = r.fallback, a = t.mode, s = e.child, c = s.sibling, r = Ji(s, {
			mode: "hidden",
			children: r.children
		}), r.subtreeFlags = s.subtreeFlags & 1206910976, c === null ? (i = Zi(i, a, n, null), i.flags |= 2) : i = Ji(c, i), i.return = t, r.return = t, r.sibling = i, t.child = r, Qc(null, r), r = t.child, i = e.child.memoizedState, i === null ? i = ll(n) : (a = i.cachePool, a === null ? a = ao() : (s = D._currentValue, a = a.parent === s ? a : {
			parent: s,
			pool: s
		}), i = {
			baseLanes: i.baseLanes | n,
			cachePool: a
		}), r.memoizedState = i, r.childLanes = ul(e, o, n), t.memoizedState = cl, Qc(e.child, r)) : (Wo(t), n = e.child, e = n.sibling, n = Ji(n, {
			mode: "visible",
			children: r.children
		}), n.return = t, n.sibling = null, e !== null && (o = t.deletions, o === null ? (t.deletions = [e], t.flags |= 16) : o.push(e)), t.child = n, t.memoizedState = null, n);
	}
	function fl(e, t) {
		return t = pl({
			mode: "visible",
			children: t
		}, e.mode), t.return = e, e.child = t;
	}
	function pl(e, t) {
		return e = Ki(22, e, null, t), e.lanes = 0, e;
	}
	function ml(e, t, n) {
		return Co(t, e.child, null, n), e = fl(t, t.pendingProps.children), e.flags |= 2, t.memoizedState = null, e;
	}
	function hl(e, t, n, r, i, o, s, c) {
		if (n) return t.flags & 256 ? (Wo(t), t.flags &= -257, ml(e, t, c)) : t.memoizedState === null ? (qo(), o = i.fallback, s = t.mode, i = pl({
			mode: "visible",
			children: i.children
		}, s), o = Zi(o, s, c, null), o.flags |= 2, i.return = t, o.return = t, i.sibling = o, t.child = i, Co(t, e.child, null, c), i = t.child, i.memoizedState = ll(c), i.childLanes = ul(e, r, c), t.memoizedState = cl, Qc(null, i)) : (qo(), t.child = e.child, t.flags |= 128, null);
		if (Wo(t), wm(o)) {
			if (r = o.nextSibling && o.nextSibling.dataset, r) var l = r.dgst;
			return r = l, r !== "" && (i = Error(a(419)), i.stack = "", i.digest = r, Oa({
				value: i,
				source: null,
				stack: null
			})), ml(e, t, c);
		}
		if (Kc || Ia(e, t, c, !1), r = (c & e.childLanes) !== 0, Kc || r) {
			if (Lo.current !== null) return ml(e, t, c);
			if (r = hd, r !== null && (i = At(r, c), i !== 0 && i !== s.retryLane)) throw s.retryLane = i, Vi(e, i), Yd(r, e, i), Gc;
			return Cm(o) || cf(), ml(e, t, c);
		}
		return Cm(o) ? (t.flags |= 192, t.child = e.child, null) : (e = s.treeContext, va = Em(o.nextSibling), _a = t, E = !0, ya = null, ba = !1, e !== null && ga(t, e), t = fl(t, i.children), t.flags |= 134221824, t);
	}
	function gl(e, t, n) {
		e.lanes |= t;
		var r = e.alternate;
		r !== null && (r.lanes |= t), Pa(e.return, t, n);
	}
	function _l(e) {
		for (var t = null; e !== null;) {
			var n = e.alternate;
			n !== null && Qo(n) === null && (t = e), e = e.sibling;
		}
		return t;
	}
	function vl(e, t, n, r, i, a) {
		var o = e.memoizedState;
		o === null ? e.memoizedState = {
			isBackwards: t,
			rendering: null,
			renderingStartTime: 0,
			last: r,
			tail: n,
			tailMode: i,
			treeForkCount: a
		} : (o.isBackwards = t, o.rendering = null, o.renderingStartTime = 0, o.last = r, o.tail = n, o.tailMode = i, o.treeForkCount = a);
	}
	function yl(e) {
		var t = e.child;
		for (e.child = null; t !== null;) {
			var n = t.sibling;
			t.sibling = e.child, e.child = t, t = n;
		}
	}
	function bl(e, t, n) {
		var r = t.pendingProps, i = r.revealOrder, a = r.tail;
		r = r.children;
		var o = Yo.current;
		if (t.flags & 128) return Xo(t, o), null;
		var s = !!(o & 2);
		if (s ? (o = o & 1 | 2, t.flags |= 128) : o &= 1, Xo(t, o), i === "backwards" && e !== null ? (yl(e), qc(e, t, r, n), yl(e)) : qc(e, t, r, n), r = E ? oa : 0, !s && e !== null && e.flags & 128) a: for (e = t.child; e !== null;) {
			if (e.tag === 13) e.memoizedState !== null && gl(e, n, t);
			else if (e.tag === 19) gl(e, n, t);
			else if (e.child !== null) {
				e.child.return = e, e = e.child;
				continue;
			}
			if (e === t) break a;
			for (; e.sibling === null;) {
				if (e.return === null || e.return === t) break a;
				e = e.return;
			}
			e.sibling.return = e.return, e = e.sibling;
		}
		switch (i) {
			case "backwards":
				n = _l(t.child), n === null ? (i = t.child, t.child = null) : (i = n.sibling, n.sibling = null, yl(t)), vl(t, !0, i, null, a, r);
				break;
			case "unstable_legacy-backwards":
				for (n = null, i = t.child, t.child = null; i !== null;) {
					if (e = i.alternate, e !== null && Qo(e) === null) {
						t.child = i;
						break;
					}
					e = i.sibling, i.sibling = n, n = i, i = e;
				}
				vl(t, !0, n, null, a, r);
				break;
			case "together":
				vl(t, !1, null, null, void 0, r);
				break;
			case "independent":
				t.memoizedState = null;
				break;
			default: n = _l(t.child), n === null ? (i = t.child, t.child = null) : (i = n.sibling, n.sibling = null), vl(t, !1, i, n, a, r);
		}
		return t.child;
	}
	function xl(e, t, n) {
		var r = t.pendingProps;
		return Ma(t, t.type, r.value), qc(e, t, r.children, n), t.child;
	}
	function Sl(e, t, n) {
		if (e !== null && (t.dependencies = e.dependencies), Sd |= t.lanes, (n & t.childLanes) === 0) {
			if (e !== null) {
				if (Ia(e, t, n, !1), (n & t.childLanes) === 0) return null;
			} else return null;
		}
		if (e !== null && t.child !== e.child) throw Error(a(153));
		if (t.child !== null) {
			for (e = t.child, n = Ji(e, e.pendingProps), t.child = n, n.return = t; e.sibling !== null;) e = e.sibling, n = n.sibling = Ji(e, e.pendingProps), n.return = t;
			n.sibling = null;
		}
		return t.child;
	}
	function Cl(e, t) {
		return (e.lanes & t) !== 0 || (e = e.dependencies, !!(e !== null && La(e)));
	}
	function wl(e, t, n) {
		switch (t.tag) {
			case 3:
				Re(t, t.stateNode.containerInfo), Ma(t, D, e.memoizedState.cache), Ea();
				break;
			case 27:
			case 5:
				Be(t);
				break;
			case 4:
				Re(t, t.stateNode.containerInfo);
				break;
			case 10:
				Ma(t, t.type, t.memoizedProps.value);
				break;
			case 31:
				if (t.memoizedState !== null) return t.flags |= 128, Go(t), null;
				break;
			case 13:
				var r = t.memoizedState;
				if (r !== null) {
					if (r.dehydrated !== null) return Wo(t), t.flags |= 128, null;
					r = Ia(e, t, n, !1);
					var i = t.child.childLanes;
					return r || (n & i) !== 0 ? dl(e, t, n) : (Wo(t), e = Sl(e, t, n), e === null ? null : e.sibling);
				}
				Wo(t);
				break;
			case 19:
				if (t.flags & 128) return bl(e, t, n);
				if (i = !!(e.flags & 128), r = (n & t.childLanes) !== 0, r ||= (Ia(e, t, n, !1), (n & t.childLanes) !== 0), i) {
					if (r) return bl(e, t, n);
					t.flags |= 128;
				}
				if (i = t.memoizedState, i !== null && (i.rendering = null, i.tail = null, i.lastEffect = null), Xo(t, Yo.current), r) break;
				return null;
			case 22: return t.lanes = 0, Zc(e, t, n, t.pendingProps);
			case 24: Ma(t, D, e.memoizedState.cache);
		}
		return Sl(e, t, n);
	}
	function Tl(e, t, n) {
		if (e !== null) {
			if (e.memoizedProps !== t.pendingProps) Kc = !0;
			else {
				if (!Cl(e, n) && !(t.flags & 128)) return Kc = !1, wl(e, t, n);
				Kc = !!(e.flags & 131072);
			}
		} else Kc = !1, E && t.flags & 1048576 && pa(t, oa, t.index);
		switch (t.lanes = 0, t.tag) {
			case 16:
				a: {
					var r = t.pendingProps;
					if (e = po(t.elementType), t.type = e, typeof e == "function") qi(e) ? (r = Fc(e, r), t.tag = 1, t = ol(null, t, e, r, n)) : (t.tag = 0, t = il(null, t, e, r, n));
					else {
						if (e != null) {
							var i = e.$$typeof;
							if (i === me) {
								t.tag = 11, t = Jc(null, t, e, r, n);
								break a;
							}
							if (i === _e) {
								t.tag = 14, t = Yc(null, t, e, r, n);
								break a;
							}
							if (i === pe) {
								t.tag = 10, t.type = e, t = xl(null, t, n);
								break a;
							}
						}
						throw t = Ee(e) || e, Error(a(306, t, ""));
					}
				}
				return t;
			case 0: return il(e, t, t.type, t.pendingProps, n);
			case 1: return r = t.type, i = Fc(r, t.pendingProps), ol(e, t, r, i, n);
			case 3:
				a: {
					if (Re(t, t.stateNode.containerInfo), e === null) throw Error(a(387));
					r = t.pendingProps;
					var o = t.memoizedState;
					i = o.element, Do(e, t), Po(t, r, null, n);
					var s = t.memoizedState;
					if (r = s.cache, Ma(t, D, r), r !== o.cache && Fa(t, [D], n, !0), No(), r = s.element, o.isDehydrated) {
						if (o = {
							element: r,
							isDehydrated: !1,
							cache: s.cache
						}, t.updateQueue.baseState = o, t.memoizedState = o, t.flags & 256) {
							t = sl(e, t, r, n);
							break a;
						}
						if (r !== i) {
							i = na(Error(a(424)), t), Oa(i), t = sl(e, t, r, n);
							break a;
						}
						switch (e = t.stateNode.containerInfo, e.nodeType) {
							case 9:
								e = e.body;
								break;
							default: e = e.nodeName === "HTML" ? e.ownerDocument.body : e;
						}
						for (va = Em(e.firstChild), _a = t, E = !0, ya = null, ba = !0, n = wo(t, null, r, n), t.child = n; n;) n.flags = n.flags & -3 | 134221824, n = n.sibling;
					} else {
						if (Ea(), r === i) {
							t = Sl(e, t, n);
							break a;
						}
						qc(e, t, r, n);
					}
					t = t.child;
				}
				return t;
			case 26: return rl(e, t), e === null ? (n = R(t.type, null, t.pendingProps, null)) ? t.memoizedState = n : E || (t.stateNode = kp(t.type, t.pendingProps, Le.current, t)) : t.memoizedState = R(t.type, e.memoizedProps, t.pendingProps, e.memoizedState), null;
			case 27: return Be(t), e === null && E && (r = t.stateNode = jm(t.type, t.pendingProps, Le.current), _a = t, ba = !0, i = va, zp(t.type) ? (Dm = i, va = Em(r.firstChild)) : va = i), qc(e, t, t.pendingProps.children, n), rl(e, t), e === null && (t.flags |= 4194304), t.child;
			case 5: return e === null && E && ((i = r = va) && (r = bm(r, t.type, t.pendingProps, ba), r === null ? i = !1 : (t.stateNode = r, _a = t, va = Em(r.firstChild), ba = !1, i = !0)), i || Sa(t)), Be(t), i = t.type, o = t.pendingProps, s = e === null ? null : e.memoizedProps, r = o.children, Ap(i, o) ? r = null : s !== null && Ap(i, s) && (t.flags |= 32), t.memoizedState !== null && (i = ds(e, t, ms, null, null, n), xh._currentValue = i), rl(e, t), qc(e, t, r, n), t.child;
			case 6: return e === null && E && ((e = n = va) && (n = xm(n, t.pendingProps, ba), n === null ? e = !1 : (t.stateNode = n, _a = t, va = null, e = !0)), e || Sa(t)), null;
			case 13: return dl(e, t, n);
			case 4: return Re(t, t.stateNode.containerInfo), r = t.pendingProps, e === null ? t.child = Co(t, null, r, n) : qc(e, t, r, n), t.child;
			case 11: return Jc(e, t, t.type, t.pendingProps, n);
			case 7: return r = t.pendingProps, rl(e, t), qc(e, t, r, n), t.child;
			case 8: return qc(e, t, t.pendingProps.children, n), t.child;
			case 12: return qc(e, t, t.pendingProps.children, n), t.child;
			case 10: return xl(e, t, n);
			case 9: return i = t.type._context, r = t.pendingProps.children, Ra(t), i = za(i), r = r(i), t.flags |= 1, qc(e, t, r, n), t.child;
			case 14: return Yc(e, t, t.type, t.pendingProps, n);
			case 15: return Xc(e, t, t.type, t.pendingProps, n);
			case 19: return bl(e, t, n);
			case 31: return nl(e, t, n);
			case 22: return Zc(e, t, n, t.pendingProps);
			case 24: return Ra(t), r = za(D), e === null ? (i = ro(), i === null && (i = hd, o = O(), i.pooledCache = o, o.refCount++, o !== null && (i.pooledCacheLanes |= n), i = o), t.memoizedState = {
				parent: r,
				cache: i
			}, Eo(t), Ma(t, D, i)) : ((e.lanes & n) !== 0 && (Do(e, t), Po(t, null, null, n), No()), i = e.memoizedState, o = t.memoizedState, i.parent === r ? (r = o.cache, Ma(t, D, r), r !== i.cache && Fa(t, [D], n, !0)) : (i = {
				parent: r,
				cache: r
			}, t.memoizedState = i, t.lanes === 0 && (t.memoizedState = t.updateQueue.baseState = i), Ma(t, D, r))), qc(e, t, t.pendingProps.children, n), t.child;
			case 30: return t.stateNode === null && (t.stateNode = {
				autoName: null,
				paired: null,
				clones: null,
				ref: null
			}), r = t.pendingProps, r.name != null && r.name !== "auto" ? t.flags |= e === null ? 18882560 : 18874368 : E && ma(t), e !== null && e.memoizedProps.name !== r.name ? t.flags |= 4194816 : rl(e, t), qc(e, t, r.children, n), t.child;
			case 29: throw t.pendingProps;
		}
		throw Error(a(156, t.tag));
	}
	function El(e) {
		e.flags |= 4;
	}
	function Dl(e, t, n, r, i) {
		var a;
		if ((a = !!(e.mode & 32)) && (a = n === null ? ch(t, r) : ch(t, r) && (r.src !== n.src || r.srcSet !== n.srcSet)), a) {
			if (e.flags |= 16777216, (i & 335544128) === i) {
				if (e.stateNode.complete) e.flags |= 8192;
				else if (af()) e.flags |= 8192;
				else throw mo = lo, so;
			}
		} else e.flags &= -16777217;
	}
	function Ol(e, t) {
		if (t.type !== "stylesheet" || t.state.loading & 4) e.flags &= -16777217;
		else if (e.flags |= 16777216, !lh(t)) {
			if (af()) e.flags |= 8192;
			else throw mo = lo, so;
		}
	}
	function kl(e, t) {
		t !== null && (e.flags |= 4), e.flags & 16384 && (t = e.tag === 22 ? 536870912 : wt(), e.lanes |= t, Ed |= t);
	}
	function Al(e, t) {
		if (!E) switch (e.tailMode) {
			case "visible": break;
			case "collapsed":
				for (var n = e.tail, r = null; n !== null;) n.alternate !== null && (r = n), n = n.sibling;
				r === null ? t || e.tail === null ? e.tail = null : e.tail.sibling = null : r.sibling = null;
				break;
			default:
				for (t = e.tail, n = null; t !== null;) t.alternate !== null && (n = t), t = t.sibling;
				n === null ? e.tail = null : n.sibling = null;
		}
	}
	function j(e) {
		var t = e.alternate !== null && e.alternate.child === e.child, n = 0, r = 0;
		if (t) for (var i = e.child; i !== null;) n |= i.lanes | i.childLanes, r |= i.subtreeFlags & 1206910976, r |= i.flags & 1206910976, i.return = e, i = i.sibling;
		else for (i = e.child; i !== null;) n |= i.lanes | i.childLanes, r |= i.subtreeFlags, r |= i.flags, i.return = e, i = i.sibling;
		return e.subtreeFlags |= r, e.childLanes = n, t;
	}
	function jl(e, t, n) {
		var r = t.pendingProps;
		switch (ha(t), t.tag) {
			case 16:
			case 15:
			case 0:
			case 11:
			case 7:
			case 8:
			case 12:
			case 9:
			case 14: return j(t), null;
			case 1: return j(t), null;
			case 3: return n = t.stateNode, r = null, e !== null && (r = e.memoizedState.cache), t.memoizedState.cache !== r && (t.flags |= 2048), Na(D), ze(), n.pendingContext && (n.context = n.pendingContext, n.pendingContext = null), (e === null || e.child === null) && (Ta(t) ? El(t) : e === null || e.memoizedState.isDehydrated && !(t.flags & 256) || (t.flags |= 1024, Da())), j(t), null;
			case 26:
				var i = t.type, o = t.memoizedState;
				return e === null ? (El(t), o === null ? (j(t), Dl(t, i, null, r, n)) : (j(t), Ol(t, o))) : o ? o === e.memoizedState ? (j(t), t.flags &= -16777217) : (El(t), j(t), Ol(t, o)) : (e = e.memoizedProps, e !== r && El(t), j(t), Dl(t, i, e, r, n)), null;
			case 27:
				if (Ve(t), n = Le.current, i = t.type, e !== null && t.stateNode != null) e.memoizedProps !== r && El(t);
				else {
					if (!r) {
						if (t.stateNode === null) throw Error(a(166));
						return j(t), t.subtreeFlags &= -33554433, null;
					}
					e = Fe.current, Ta(t) ? Ca(t, e) : (e = jm(i, r, n), t.stateNode = e, El(t));
				}
				return j(t), t.subtreeFlags &= -33554433, null;
			case 5:
				if (Ve(t), i = t.type, e !== null && t.stateNode != null) e.memoizedProps !== r && El(t);
				else {
					if (!r) {
						if (t.stateNode === null) throw Error(a(166));
						return j(t), t.subtreeFlags &= -33554433, null;
					}
					if (o = Fe.current, Ta(t)) Ca(t, o);
					else {
						var s = Ep(Le.current);
						switch (o) {
							case 1:
								o = s.createElementNS("http://www.w3.org/2000/svg", i);
								break;
							case 2:
								o = s.createElementNS("http://www.w3.org/1998/Math/MathML", i);
								break;
							default: switch (i) {
								case "svg":
									o = s.createElementNS("http://www.w3.org/2000/svg", i);
									break;
								case "math":
									o = s.createElementNS("http://www.w3.org/1998/Math/MathML", i);
									break;
								case "script":
									o = s.createElement("div"), o.innerHTML = "<script><\/script>", o = o.removeChild(o.firstChild);
									break;
								case "select":
									o = typeof r.is == "string" ? s.createElement("select", { is: r.is }) : s.createElement("select"), r.multiple ? o.multiple = !0 : r.size && (o.size = r.size);
									break;
								default: o = typeof r.is == "string" ? s.createElement(i, { is: r.is }) : s.createElement(i);
							}
						}
						o[It] = t, o[Lt] = r;
						a: for (s = t.child; s !== null;) {
							if (s.tag === 5 || s.tag === 6) o.appendChild(s.stateNode);
							else if (s.tag !== 4 && s.tag !== 27 && s.child !== null) {
								s.child.return = s, s = s.child;
								continue;
							}
							if (s === t) break a;
							for (; s.sibling === null;) {
								if (s.return === null || s.return === t) break a;
								s = s.return;
							}
							s.sibling.return = s.return, s = s.sibling;
						}
						t.stateNode = o;
						a: switch (yp(o, i, r), i) {
							case "button":
							case "input":
							case "select":
							case "textarea":
								r = !!r.autoFocus;
								break a;
							case "img":
								r = !0;
								break a;
							default: r = !1;
						}
						r && El(t);
					}
				}
				return j(t), t.subtreeFlags &= -33554433, Dl(t, t.type, e === null ? null : e.memoizedProps, t.pendingProps, n), null;
			case 6:
				if (e && t.stateNode != null) e.memoizedProps !== r && El(t);
				else {
					if (typeof r != "string" && t.stateNode === null) throw Error(a(166));
					if (e = Le.current, Ta(t)) {
						if (e = t.stateNode, n = t.memoizedProps, r = null, i = _a, i !== null) switch (i.tag) {
							case 27:
							case 5: r = i.memoizedProps;
						}
						e[It] = t, e = !!(e.nodeValue === n || r !== null && !0 === r.suppressHydrationWarning || gp(e.nodeValue, n)), e || Sa(t, !0);
					} else e = Ep(e).createTextNode(r), e[It] = t, t.stateNode = e;
				}
				return j(t), null;
			case 31:
				if (n = t.memoizedState, e === null || e.memoizedState !== null) {
					if (r = Ta(t), n !== null) {
						if (e === null) {
							if (!r) throw Error(a(318));
							if (e = t.memoizedState, e = e === null ? null : e.dehydrated, !e) throw Error(a(557));
							e[It] = t;
						} else Ea(), !(t.flags & 128) && (t.memoizedState = null), t.flags |= 4;
						j(t), e = !1;
					} else n = Da(), e !== null && e.memoizedState !== null && (e.memoizedState.hydrationErrors = n), e = !0;
					if (!e) return t.flags & 256 ? (Jo(t), t) : (Jo(t), null);
					if (t.flags & 128) throw Error(a(558));
				}
				return j(t), null;
			case 13:
				if (r = t.memoizedState, e === null || e.memoizedState !== null && e.memoizedState.dehydrated !== null) {
					if (i = Ta(t), r !== null && r.dehydrated !== null) {
						if (e === null) {
							if (!i) throw Error(a(318));
							if (i = t.memoizedState, i = i === null ? null : i.dehydrated, !i) throw Error(a(317));
							i[It] = t;
						} else Ea(), !(t.flags & 128) && (t.memoizedState = null), t.flags |= 4;
						j(t), i = !1;
					} else i = Da(), e !== null && e.memoizedState !== null && (e.memoizedState.hydrationErrors = i), i = !0;
					if (!i) return t.flags & 256 ? (Jo(t), t) : (Jo(t), null);
				}
				return Jo(t), t.flags & 128 ? (t.lanes = n, t) : (n = r !== null, e = e !== null && e.memoizedState !== null, n && (r = t.child, i = null, r.alternate !== null && r.alternate.memoizedState !== null && r.alternate.memoizedState.cachePool !== null && (i = r.alternate.memoizedState.cachePool.pool), o = null, r.memoizedState !== null && r.memoizedState.cachePool !== null && (o = r.memoizedState.cachePool.pool), o !== i && (r.flags |= 2048)), n !== e && n && (t.child.flags |= 8192), kl(t, t.updateQueue), j(t), null);
			case 4: return ze(), e === null && op(t.stateNode.containerInfo), t.flags |= 67108864, j(t), null;
			case 10: return Na(t.type), j(t), null;
			case 19:
				if (Zo(t), r = t.memoizedState, r === null) return j(t), null;
				if (i = !!(t.flags & 128), o = r.rendering, o === null) {
					if (i) Al(r, !1);
					else {
						if (xd !== 0 || e !== null && e.flags & 128) for (e = t.child; e !== null;) {
							if (o = Qo(e), o !== null) {
								for (t.flags |= 128, Al(r, !1), e = o.updateQueue, t.updateQueue = e, kl(t, e), t.subtreeFlags = 0, e = n, n = t.child; n !== null;) Yi(n, e), n = n.sibling;
								return Xo(t, Yo.current & 1 | 2), E && fa(t, r.treeForkCount), t.child;
							}
							e = e.sibling;
						}
						r.tail !== null && et() > Md && (t.flags |= 128, i = !0, Al(r, !1), t.lanes = 4194304);
					}
				} else {
					if (!i) {
						if (e = Qo(o), e !== null) {
							if (t.flags |= 128, i = !0, e = e.updateQueue, t.updateQueue = e, kl(t, e), Al(r, !0), r.tail === null && r.tailMode !== "collapsed" && r.tailMode !== "visible" && !o.alternate && !E) return j(t), null;
						} else 2 * et() - r.renderingStartTime > Md && n !== 536870912 && (t.flags |= 128, i = !0, Al(r, !1), t.lanes = 4194304);
					}
					r.isBackwards ? (o.sibling = t.child, t.child = o) : (e = r.last, e === null ? t.child = o : e.sibling = o, r.last = o);
				}
				if (r.tail !== null) {
					e = r.tail;
					a: {
						for (n = e; n !== null;) {
							if (n.alternate !== null) {
								n = !1;
								break a;
							}
							n = n.sibling;
						}
						n = !0;
					}
					return r.rendering = e, r.tail = e.sibling, r.renderingStartTime = et(), e.sibling = null, o = Yo.current, o = i ? o & 1 | 2 : o & 1, r.tailMode === "visible" || r.tailMode === "collapsed" || !n || E ? Xo(t, o) : (n = o, Pe(Ho, t), Pe(Yo, n), Uo === null && (Uo = t)), E && fa(t, r.treeForkCount), e;
				}
				return j(t), null;
			case 22:
			case 23: return Jo(t), Vo(), r = t.memoizedState !== null, e === null ? r && (t.flags |= 8192) : e.memoizedState !== null !== r && (t.flags |= 8192), r ? n & 536870912 && !(t.flags & 128) && (j(t), t.subtreeFlags & 6 && (t.flags |= 8192)) : j(t), n = t.updateQueue, n !== null && kl(t, n.retryQueue), n = null, e !== null && e.memoizedState !== null && e.memoizedState.cachePool !== null && (n = e.memoizedState.cachePool.pool), r = null, t.memoizedState !== null && t.memoizedState.cachePool !== null && (r = t.memoizedState.cachePool.pool), r !== n && (t.flags |= 2048), e !== null && Ne(k), null;
			case 24: return n = null, e !== null && (n = e.memoizedState.cache), t.memoizedState.cache !== n && (t.flags |= 2048), Na(D), j(t), null;
			case 25: return null;
			case 30: return t.flags |= 33554432, j(t), null;
		}
		throw Error(a(156, t.tag));
	}
	function Ml(e, t) {
		switch (ha(t), t.tag) {
			case 1: return e = t.flags, e & 65536 ? (t.flags = e & -65537 | 128, t) : null;
			case 3: return Na(D), ze(), e = t.flags, e & 65536 && !(e & 128) ? (t.flags = e & -65537 | 128, t) : null;
			case 26:
			case 27:
			case 5: return Ve(t), null;
			case 31:
				if (t.memoizedState !== null) {
					if (Jo(t), t.alternate === null) throw Error(a(340));
					Ea();
				}
				return e = t.flags, e & 65536 ? (t.flags = e & -65537 | 128, t) : null;
			case 13:
				if (Jo(t), e = t.memoizedState, e !== null && e.dehydrated !== null) {
					if (t.alternate === null) throw Error(a(340));
					Ea();
				}
				return e = t.flags, e & 65536 ? (t.flags = e & -65537 | 128, t) : null;
			case 19: return Zo(t), e = t.flags, e & 65536 ? (t.flags = e & -65537 | 128, e = t.memoizedState, e !== null && (e.rendering = null, e.tail = null), t.flags |= 4, t) : null;
			case 4: return ze(), null;
			case 10: return Na(t.type), null;
			case 22:
			case 23: return Jo(t), Vo(), e !== null && Ne(k), e = t.flags, e & 65536 ? (t.flags = e & -65537 | 128, t) : null;
			case 24: return Na(D), null;
			case 25: return null;
			default: return null;
		}
	}
	function Nl(e, t) {
		switch (ha(t), t.tag) {
			case 3:
				Na(D), ze();
				break;
			case 26:
			case 27:
			case 5:
				Ve(t);
				break;
			case 4:
				ze();
				break;
			case 31:
				t.memoizedState !== null && Jo(t);
				break;
			case 13:
				Jo(t);
				break;
			case 19:
				Zo(t);
				break;
			case 10:
				Na(t.type);
				break;
			case 22:
			case 23:
				Jo(t), Vo(), e !== null && Ne(k);
				break;
			case 24: Na(D);
		}
	}
	function Pl(e, t) {
		try {
			var n = t.updateQueue, r = n === null ? null : n.lastEffect;
			if (r !== null) {
				var i = r.next;
				n = i;
				do {
					if ((n.tag & e) === e) {
						r = void 0;
						var a = n.create, o = n.inst;
						r = a(), o.destroy = r;
					}
					n = n.next;
				} while (n !== i);
			}
		} catch (e) {
			kf(t, t.return, e);
		}
	}
	function Fl(e, t, n) {
		try {
			var r = t.updateQueue, i = r === null ? null : r.lastEffect;
			if (i !== null) {
				var a = i.next;
				r = a;
				do {
					if ((r.tag & e) === e) {
						var o = r.inst, s = o.destroy;
						if (s !== void 0) {
							o.destroy = void 0, i = t;
							var c = n, l = s;
							try {
								l();
							} catch (e) {
								kf(i, c, e);
							}
						}
					}
					r = r.next;
				} while (r !== a);
			}
		} catch (e) {
			kf(t, t.return, e);
		}
	}
	function Il(e) {
		var t = e.updateQueue;
		if (t !== null) {
			var n = e.stateNode;
			try {
				Io(t, n);
			} catch (t) {
				kf(e, e.return, t);
			}
		}
	}
	function Ll(e, t, n) {
		n.props = Fc(e.type, e.memoizedProps), n.state = e.memoizedState;
		try {
			n.componentWillUnmount();
		} catch (n) {
			kf(e, t, n);
		}
	}
	function Rl(e, t) {
		try {
			var n = e.ref;
			if (n !== null) {
				switch (e.tag) {
					case 26:
					case 27:
					case 5:
						var r = e.stateNode;
						break;
					case 30:
						var i = e.stateNode, a = ji(e.memoizedProps, i);
						(i.ref === null || i.ref.name !== a) && (i.ref = Zp(a)), r = i.ref;
						break;
					case 7:
						if (e.stateNode === null) {
							var o = new Qp(e);
							h(e.child, !1, hm, o, void 0, void 0), e.stateNode = o;
						}
						r = e.stateNode;
						break;
					default: r = e.stateNode;
				}
				typeof n == "function" ? e.refCleanup = n(r) : n.current = r;
			}
		} catch (n) {
			kf(e, t, n);
		}
	}
	function zl(e, t) {
		var n = e.ref, r = e.refCleanup;
		if (n !== null) {
			if (typeof r == "function") try {
				r();
			} catch (n) {
				kf(e, t, n);
			} finally {
				e.refCleanup = null, e = e.alternate, e != null && (e.refCleanup = null);
			}
			else if (typeof n == "function") try {
				n(null);
			} catch (n) {
				kf(e, t, n);
			}
			else n.current = null;
		}
	}
	function Bl(e, t) {
		if ((e.tag === 5 || e.tag === 27 || e.tag === 6) && e.alternate === null && t !== null) for (var n = 0; n < t.length; n++) _m(e.stateNode, t[n]);
	}
	function Vl(e) {
		for (var t = e.return; t !== null && (Wl(t) && _m(e.stateNode, t.stateNode), !Ul(t));) t = t.return;
	}
	function Hl(e) {
		for (var t = e.return; t !== null && (Wl(t) && vm(e.stateNode, t.stateNode), !Ul(t));) t = t.return;
	}
	function Ul(e) {
		return e.tag === 5 || e.tag === 3 || e.tag === 27;
	}
	function Wl(e) {
		return e && e.tag === 7 && e.stateNode !== null;
	}
	function Gl(e) {
		var t = e.type, n = e.memoizedProps, r = e.stateNode;
		try {
			a: switch (t) {
				case "button":
				case "input":
				case "select":
				case "textarea":
					n.autoFocus && r.focus();
					break a;
				case "img": n.src ? r.src = n.src : n.srcSet && (r.srcset = n.srcSet);
			}
		} catch (t) {
			kf(e, e.return, t);
		}
	}
	function Kl(e, t, n) {
		try {
			var r = e.stateNode;
			xp(r, e.type, n, t), r[Lt] = t;
		} catch (t) {
			kf(e, e.return, t);
		}
	}
	function ql(e) {
		return e.tag === 5 || e.tag === 3 || e.tag === 26 || e.tag === 27 && zp(e.type) || e.tag === 4;
	}
	function Jl(e) {
		a: for (;;) {
			for (; e.sibling === null;) {
				if (e.return === null || ql(e.return)) return null;
				e = e.return;
			}
			for (e.sibling.return = e.return, e = e.sibling; e.tag !== 5 && e.tag !== 6 && e.tag !== 18;) {
				if (e.tag === 27 && zp(e.type) || e.flags & 2 || e.child === null || e.tag === 4) continue a;
				e.child.return = e, e = e.child;
			}
			if (!(e.flags & 2)) return e.stateNode;
		}
	}
	function Yl(e, t, n, r) {
		var i = e.tag;
		if (i === 5 || i === 6) i = e.stateNode, t ? (n.nodeType === 9 ? n.body : n.nodeName === "HTML" ? n.ownerDocument.body : n).insertBefore(i, t) : (t = n.nodeType === 9 ? n.body : n.nodeName === "HTML" ? n.ownerDocument.body : n, t.appendChild(i), n = n._reactRootContainer, n != null || t.onclick !== null || (t.onclick = Mn)), Bl(e, r), T = !0;
		else if (i !== 4 && (i === 27 && (Bl(e, r), r = null, zp(e.type) && (n = e.stateNode, t = null)), e = e.child, e !== null)) for (Yl(e, t, n, r), e = e.sibling; e !== null;) Yl(e, t, n, r), e = e.sibling;
	}
	function Xl(e, t, n, r) {
		var i = e.tag;
		if (i === 5 || i === 6) i = e.stateNode, t ? n.insertBefore(i, t) : n.appendChild(i), Bl(e, r), T = !0;
		else if (i !== 4 && (i === 27 && (Bl(e, r), r = null, zp(e.type) && (n = e.stateNode)), e = e.child, e !== null)) for (Xl(e, t, n, r), e = e.sibling; e !== null;) Xl(e, t, n, r), e = e.sibling;
	}
	function Zl(e) {
		var t = e.stateNode, n = e.memoizedProps;
		try {
			for (var r = e.type, i = t.attributes; i.length;) t.removeAttributeNode(i[0]);
			yp(t, r, n), t[It] = e, t[Lt] = n;
		} catch (t) {
			kf(e, e.return, t);
		}
	}
	var Ql = !1, $l = null;
	function eu(e) {
		(e.tag === 30 || e.subtreeFlags & 33554432) && (Ql = !0);
	}
	var tu = null;
	function nu() {
		var e = tu;
		return tu = null, e;
	}
	var ru = 0;
	function iu(e, t, n, r, i) {
		return ru = 0, au(e.child, t, n, r, i);
	}
	function au(e, t, n, r, i) {
		for (var a = !1; e !== null;) {
			if (e.tag === 5) {
				var o = e.stateNode;
				if (r !== null) {
					var s = Gp(o);
					r.push(s), s.view && (a = !0);
				} else a || Gp(o).view && (a = !0);
				Ql = !0, Hp(o, ru === 0 ? t : t + "_" + ru, n), ru++;
			} else e.tag === 22 && e.memoizedState !== null || e.tag === 30 && i || au(e.child, t, n, r, i) && (a = !0);
			e = e.sibling;
		}
		return a;
	}
	function ou(e, t) {
		for (; e !== null;) e.tag === 5 ? Up(e.stateNode, e.memoizedProps) : e.tag === 22 && e.memoizedState !== null || e.tag === 30 && t || ou(e.child, t), e = e.sibling;
	}
	function su(e) {
		if (e.subtreeFlags & 18874368) for (e = e.child; e !== null;) {
			if ((e.tag !== 22 || e.memoizedState === null) && (su(e), e.tag === 30 && e.flags & 18874368 && e.stateNode.paired)) {
				var t = e.memoizedProps;
				if (t.name == null || t.name === "auto") throw Error(a(544));
				var n = t.name;
				t = Ni(t.default, t.share), t !== "none" && (iu(e, n, t, null, !1) || ou(e.child, !1));
			}
			e = e.sibling;
		}
	}
	function cu(e, t) {
		if (e.tag === 30) {
			var n = e.stateNode, r = e.memoizedProps, i = ji(r, n), a = Ni(r.default, n.paired ? r.share : r.enter);
			a === "none" ? su(e) : iu(e, i, a, null, !1) ? (su(e), n.paired || t || Jd(e, r.onEnter)) : ou(e.child, !1);
		} else if (e.subtreeFlags & 33554432) for (e = e.child; e !== null;) cu(e, t), e = e.sibling;
		else su(e);
	}
	function lu(e) {
		if ($l !== null && $l.size !== 0) {
			var t = $l;
			if (e.subtreeFlags & 18874368) for (e = e.child; e !== null;) {
				if (e.tag !== 22 || e.memoizedState === null) {
					if (e.tag === 30 && e.flags & 18874368) {
						var n = e.memoizedProps, r = n.name;
						if (r != null && r !== "auto") {
							var i = t.get(r);
							if (i !== void 0) {
								var a = Ni(n.default, n.share);
								if (a !== "none" && (iu(e, r, a, null, !1) ? (a = e.stateNode, i.paired = a, a.paired = i, Jd(e, n.onShare)) : ou(e.child, !1)), t.delete(r), t.size === 0) break;
							}
						}
					}
					lu(e);
				}
				e = e.sibling;
			}
		}
	}
	function uu(e) {
		if (e.tag === 30) {
			var t = e.memoizedProps, n = ji(t, e.stateNode), r = $l === null ? void 0 : $l.get(n), i = Ni(t.default, r === void 0 ? t.exit : t.share);
			i !== "none" && (iu(e, n, i, null, !1) ? r === void 0 ? Jd(e, t.onExit) : (i = e.stateNode, r.paired = i, i.paired = r, $l.delete(n), Jd(e, t.onShare)) : ou(e.child, !1)), $l !== null && lu(e);
		} else if (e.subtreeFlags & 33554432) for (e = e.child; e !== null;) uu(e), e = e.sibling;
		else $l !== null && lu(e);
	}
	function du(e) {
		for (e = e.child; e !== null;) {
			if (e.tag === 30) {
				var t = e.memoizedProps, n = ji(t, e.stateNode);
				t = Ni(t.default, t.update), e.flags &= -5, t !== "none" && iu(e, n, t, e.memoizedState = [], !1);
			} else e.subtreeFlags & 33554432 && du(e);
			e = e.sibling;
		}
	}
	function fu(e) {
		if (e.subtreeFlags & 18874368) for (e = e.child; e !== null;) {
			if (e.tag !== 22 || e.memoizedState === null) {
				if (e.tag === 30 && e.flags & 18874368) {
					var t = e.stateNode;
					t.paired !== null && (t.paired = null, ou(e.child, !1));
				}
				fu(e);
			}
			e = e.sibling;
		}
	}
	function pu(e) {
		if (e.tag === 30) e.stateNode.paired = null, ou(e.child, !1), fu(e);
		else if (e.subtreeFlags & 33554432) for (e = e.child; e !== null;) pu(e), e = e.sibling;
		else fu(e);
	}
	function mu(e) {
		for (e = e.child; e !== null;) e.tag === 30 ? ou(e.child, !1) : e.subtreeFlags & 33554432 && mu(e), e = e.sibling;
	}
	function hu(e, t, n, r, i, a, o) {
		for (var s = !1; t !== null;) {
			if (t.tag === 5) {
				var c = t.stateNode;
				if (a !== null && ru < a.length) {
					var l = a[ru], u = Gp(c);
					(l.view || u.view) && (s = !0);
					var d;
					if (d = !(e.flags & 4)) {
						if (u.clip) d = !0;
						else {
							d = l.rect;
							var f = u.rect;
							d = d.y !== f.y || d.x !== f.x || d.height !== f.height || d.width !== f.width;
						}
					}
					d && (e.flags |= 4), u.abs ? u = !l.abs : (l = l.rect, u = u.rect, u = l.height !== u.height || l.width !== u.width), u && (e.flags |= 32);
				} else e.flags |= 32;
				e.flags & 4 && Hp(c, ru === 0 ? n : n + "_" + ru, i), s && e.flags & 4 || (tu === null && (tu = []), tu.push(c, ru === 0 ? r : r + "_" + ru, t.memoizedProps)), ru++;
			} else (t.tag !== 22 || t.memoizedState === null) && (t.tag === 30 && o ? e.flags |= t.flags & 32 : hu(e, t.child, n, r, i, a, o) && (s = !0));
			t = t.sibling;
		}
		return s;
	}
	function gu(e, t) {
		for (e = e.child; e !== null;) {
			if (e.tag === 30) {
				var n = e.memoizedProps, r = e.stateNode, i = ji(n, r), a = Ni(n.default, n.update);
				if (t) {
					r = r.clones;
					var o = r === null ? null : r.map(Kp);
				} else o = e.memoizedState, e.memoizedState = null;
				r = e;
				var s = e.child;
				ru = 0, i = hu(r, s, i, i, a, o, !1), e.flags & 4 && i && (t || Jd(e, n.onUpdate));
			} else e.subtreeFlags & 33554432 && gu(e, t);
			e = e.sibling;
		}
	}
	var _u = !1, vu = !1, yu = !1, bu = !1, xu = typeof WeakSet == "function" ? WeakSet : Set, Su = null, Cu = !1, wu = !1, Tu = !1, Eu = !1;
	function Du(e, t, n) {
		if (e = e.containerInfo, wp = Ah, e = si(e), ci(e)) {
			if ("selectionStart" in e) var r = {
				start: e.selectionStart,
				end: e.selectionEnd
			};
			else a: {
				r = (r = e.ownerDocument) && r.defaultView || window;
				var i = r.getSelection && r.getSelection();
				if (i && i.rangeCount !== 0) {
					r = i.anchorNode;
					var a = i.anchorOffset, o = i.focusNode;
					i = i.focusOffset;
					try {
						r.nodeType, o.nodeType;
					} catch {
						r = null;
						break a;
					}
					var s = 0, c = -1, l = -1, u = 0, d = 0, f = e, p = null;
					b: for (;;) {
						for (var m; f !== r || a !== 0 && f.nodeType !== 3 || (c = s + a), f !== o || i !== 0 && f.nodeType !== 3 || (l = s + i), f.nodeType === 3 && (s += f.nodeValue.length), (m = f.firstChild) !== null;) p = f, f = m;
						for (;;) {
							if (f === e) break b;
							if (p === r && ++u === a && (c = s), p === o && ++d === i && (l = s), (m = f.nextSibling) !== null) break;
							f = p, p = f.parentNode;
						}
						f = m;
					}
					r = c === -1 || l === -1 ? null : {
						start: c,
						end: l
					};
				} else r = null;
			}
			r ||= {
				start: 0,
				end: 0
			};
		} else r = null;
		for (Tp = {
			focusedElem: e,
			selectionRange: r
		}, Ah = !1, n = (n & 335544064) === n, Su = t, t = n ? 9270 : 1024; Su !== null;) {
			if (e = Su, n && (r = e.deletions, r !== null)) for (a = 0; a < r.length; a++) n && uu(r[a]);
			if (e.alternate === null && e.flags & 2) n && eu(e), Ou(n);
			else {
				if (e.tag === 22) {
					if (r = e.alternate, e.memoizedState !== null) {
						r !== null && r.memoizedState === null && n && uu(r), Ou(n);
						continue;
					}
					if (r !== null && r.memoizedState !== null) {
						n && eu(e), Ou(n);
						continue;
					}
				}
				r = e.child, (e.subtreeFlags & t) !== 0 && r !== null ? (r.return = e, Su = r) : (n && du(e), Ou(n));
			}
		}
		$l = null;
	}
	function Ou(e) {
		for (; Su !== null;) {
			var t = Su, n = e, r = t.alternate, i = t.flags;
			switch (t.tag) {
				case 0:
				case 11:
				case 15: break;
				case 1:
					if (i & 1024 && r !== null) {
						n = void 0, i = r.memoizedProps, r = r.memoizedState;
						var o = t.stateNode;
						try {
							var s = Fc(t.type, i);
							n = o.getSnapshotBeforeUpdate(s, r), o.__reactInternalSnapshotBeforeUpdate = n;
						} catch (e) {
							kf(t, t.return, e);
						}
					}
					break;
				case 3:
					if (i & 1024) {
						if (r = t.stateNode.containerInfo, n = r.nodeType, n === 9) ym(r);
						else if (n === 1) switch (r.nodeName) {
							case "HEAD":
							case "HTML":
							case "BODY":
								ym(r);
								break;
							default: r.textContent = "";
						}
					}
					break;
				case 5:
				case 26:
				case 27:
				case 6:
				case 4:
				case 17: break;
				case 30:
					n && r !== null && (n = ji(r.memoizedProps, r.stateNode), i = t.memoizedProps, i = Ni(i.default, i.update), i !== "none" && iu(r, n, i, r.memoizedState = [], !0));
					break;
				default: if (i & 1024) throw Error(a(163));
			}
			if (r = t.sibling, r !== null) {
				r.return = t.return, Su = r;
				break;
			}
			Su = t.return;
		}
	}
	function ku(e, t, n) {
		var r = n.flags;
		switch (n.tag) {
			case 0:
			case 11:
			case 15:
				Yu(e, n), r & 4 && Pl(5, n);
				break;
			case 1:
				if (Yu(e, n), r & 4) {
					if (e = n.stateNode, t === null) try {
						e.componentDidMount();
					} catch (e) {
						kf(n, n.return, e);
					}
					else {
						var i = Fc(n.type, t.memoizedProps);
						t = t.memoizedState;
						try {
							e.componentDidUpdate(i, t, e.__reactInternalSnapshotBeforeUpdate);
						} catch (e) {
							kf(n, n.return, e);
						}
					}
				}
				r & 64 && Il(n), r & 512 && Rl(n, n.return);
				break;
			case 3:
				if (Yu(e, n), r & 64 && (e = n.updateQueue, e !== null)) {
					if (t = null, n.child !== null) switch (n.child.tag) {
						case 27:
						case 5:
							t = n.child.stateNode;
							break;
						case 1: t = n.child.stateNode;
					}
					try {
						Io(e, t);
					} catch (e) {
						kf(n, n.return, e);
					}
				}
				break;
			case 27: t === null && r & 4 && Zl(n);
			case 26:
			case 5:
				Yu(e, n), t === null && r & 4 && Gl(n), r & 512 && Rl(n, n.return);
				break;
			case 12:
				Yu(e, n);
				break;
			case 31:
				Yu(e, n), r & 4 && Ru(e, n);
				break;
			case 13:
				Yu(e, n), r & 4 && zu(e, n), r & 64 && (e = n.memoizedState, e !== null && (e = e.dehydrated, e !== null && (n = Nf.bind(null, n), Tm(e, n))));
				break;
			case 22:
				if (r = n.memoizedState !== null || _u, !r) {
					var a = t !== null && t.memoizedState !== null || vu;
					t = _u, i = vu, _u = r, (vu = a) && !i ? (r = 2, n.subtreeFlags & 8772 && (r |= 1), Zu(e, n, r)) : Yu(e, n), _u = t, vu = i;
				}
				break;
			case 30:
				Yu(e, n), r & 512 && Rl(n, n.return);
				break;
			case 7: r & 512 && Rl(n, n.return);
			default: Yu(e, n);
		}
	}
	function Au(e, t) {
		for (e = e.child; e !== null;) ju(e, t), e = e.sibling;
	}
	function ju(e, t) {
		switch (e.tag) {
			case 5:
			case 26:
				try {
					var n = e.stateNode;
					if (t) {
						var r = n.style;
						typeof r.setProperty == "function" ? r.setProperty("display", "none", "important") : r.display = "none";
					} else {
						var i = e.stateNode, a = e.memoizedProps.style, o = a != null && a.hasOwnProperty("display") ? a.display : null;
						i.style.display = o == null || typeof o == "boolean" ? "" : ("" + o).trim();
					}
				} catch (t) {
					kf(e, e.return, t);
				}
				Mu(e, t);
				break;
			case 6:
				try {
					e.stateNode.nodeValue = t ? "" : e.memoizedProps, T = !0;
				} catch (t) {
					kf(e, e.return, t);
				}
				break;
			case 18:
				try {
					var s = e.stateNode;
					t ? Vp(s, !0) : Vp(e.stateNode, !1);
				} catch (t) {
					kf(e, e.return, t);
				}
				break;
			case 22:
			case 23:
				e.memoizedState === null && Au(e, t);
				break;
			default: Au(e, t);
		}
	}
	function Mu(e, t) {
		if (e.subtreeFlags & 67108864) for (e = e.child; e !== null;) {
			a: {
				var n = e, r = t;
				switch (n.tag) {
					case 4:
						ju(n, r);
						break a;
					case 22:
						n.memoizedState === null && Mu(n, r);
						break a;
					default: Mu(n, r);
				}
			}
			e = e.sibling;
		}
	}
	function Nu(e) {
		var t = e.alternate;
		t !== null && (e.alternate = null, Nu(t)), e.child = null, e.deletions = null, e.sibling = null, e.tag === 5 && (t = e.stateNode, t !== null && Gt(t)), e.stateNode = null, e.return = null, e.dependencies = null, e.memoizedProps = null, e.memoizedState = null, e.pendingProps = null, e.stateNode = null, e.updateQueue = null;
	}
	var Pu = null, Fu = !1;
	function Iu(e, t, n) {
		for (n = n.child; n !== null;) Lu(e, t, n), n = n.sibling;
	}
	function Lu(e, t, n) {
		if (ut && typeof ut.onCommitFiberUnmount == "function") try {
			ut.onCommitFiberUnmount(lt, n);
		} catch {}
		switch (n.tag) {
			case 26:
				vu || zl(n, t), Iu(e, t, n), n.memoizedState ? n.memoizedState.count-- : n.stateNode && !vu && (n = n.stateNode, n.parentNode.removeChild(n));
				break;
			case 27:
				vu || zl(n, t), Hl(n);
				var r = Pu, i = Fu;
				zp(n.type) && (Pu = n.stateNode, Fu = !1), Iu(e, t, n), Mm(n.stateNode, n.type, n.memoizedProps), Pu = r, Fu = i;
				break;
			case 5: vu || zl(n, t), Hl(n);
			case 6:
				if (n.tag === 6 && Hl(n), r = Pu, i = Fu, Pu = null, Iu(e, t, n), Pu = r, Fu = i, Pu !== null) {
					if (Fu) try {
						(Pu.nodeType === 9 ? Pu.body : Pu.nodeName === "HTML" ? Pu.ownerDocument.body : Pu).removeChild(n.stateNode), T = !0;
					} catch (e) {
						kf(n, t, e);
					}
					else try {
						Pu.removeChild(n.stateNode), T = !0;
					} catch (e) {
						kf(n, t, e);
					}
				}
				break;
			case 18:
				Pu !== null && (Fu ? (e = Pu, Bp(e.nodeType === 9 ? e.body : e.nodeName === "HTML" ? e.ownerDocument.body : e, n.stateNode), ng(e)) : Bp(Pu, n.stateNode));
				break;
			case 4:
				r = Pu, i = Fu, Pu = n.stateNode.containerInfo, Fu = !0, Iu(e, t, n), Pu = r, Fu = i;
				break;
			case 0:
			case 11:
			case 14:
			case 15:
				Fl(2, n, t), vu || Fl(4, n, t), Iu(e, t, n);
				break;
			case 1:
				vu || (zl(n, t), r = n.stateNode, typeof r.componentWillUnmount == "function" && Ll(n, t, r)), Iu(e, t, n);
				break;
			case 21:
				Iu(e, t, n);
				break;
			case 22:
				vu = (r = vu) || n.memoizedState !== null, Iu(e, t, n), vu = r;
				break;
			case 30:
				zl(n, t), Iu(e, t, n);
				break;
			case 7:
				vu || zl(n, t), Iu(e, t, n);
				break;
			default: Iu(e, t, n);
		}
	}
	function Ru(e, t) {
		if (t.memoizedState === null && (e = t.alternate, e !== null && (e = e.memoizedState, e !== null))) {
			e = e.dehydrated;
			try {
				ng(e);
			} catch (e) {
				kf(t, t.return, e);
			}
		}
	}
	function zu(e, t) {
		if (t.memoizedState === null && (e = t.alternate, e !== null && (e = e.memoizedState, e !== null && (e = e.dehydrated, e !== null)))) try {
			ng(e);
		} catch (e) {
			kf(t, t.return, e);
		}
	}
	function Bu(e) {
		switch (e.tag) {
			case 31:
			case 13:
			case 19:
				var t = e.stateNode;
				return t === null && (t = e.stateNode = new xu()), t;
			case 22: return e = e.stateNode, t = e._retryCache, t === null && (t = e._retryCache = new xu()), t;
			default: throw Error(a(435, e.tag));
		}
	}
	function Vu(e, t) {
		var n = Bu(e);
		t.forEach(function(t) {
			if (!n.has(t)) {
				n.add(t);
				var r = Pf.bind(null, e, t);
				t.then(r, r);
			}
		});
	}
	function Hu(e, t, n) {
		var r = t.deletions;
		if (r !== null) for (var i = 0; i < r.length; i++) {
			var o = r[i], s = e, c = t, l = c;
			a: for (; l !== null;) {
				switch (l.tag) {
					case 27:
						if (zp(l.type)) {
							Pu = l.stateNode, Fu = !1;
							break a;
						}
						break;
					case 5:
						Pu = l.stateNode, Fu = !1;
						break a;
					case 3:
					case 4:
						Pu = l.stateNode.containerInfo, Fu = !0;
						break a;
				}
				l = l.return;
			}
			if (Pu === null) throw Error(a(160));
			Lu(s, c, o), Pu = null, Fu = !1, s = o.alternate, s !== null && (s.return = null), o.return = null;
		}
		if (t.subtreeFlags & 13886) for (t = t.child; t !== null;) Wu(t, e, n), t = t.sibling;
	}
	var Uu = null;
	function Wu(e, t, n) {
		var r = e.alternate, i = e.flags;
		switch (e.tag) {
			case 0:
			case 11:
			case 14:
			case 15:
				if (i & 4 && (r = e.updateQueue, r = r === null ? null : r.events, r !== null)) for (var o = 0; o < r.length; o++) {
					var s = r[o];
					s.ref.impl = s.nextImpl;
				}
				Hu(t, e, n), Gu(e), i & 4 && (Fl(3, e, e.return), Pl(3, e), Fl(5, e, e.return));
				break;
			case 1:
				Hu(t, e, n), Gu(e), i & 512 && (vu || r === null || zl(r, r.return)), i & 64 && _u && (e = e.updateQueue, e !== null && (t = e.callbacks, t !== null && (n = e.shared.hiddenCallbacks, e.shared.hiddenCallbacks = n === null ? t : n.concat(t))));
				break;
			case 26:
				if (o = Uu, Hu(t, e, n), Gu(e), i & 512 && (vu || r === null || zl(r, r.return)), i & 4) {
					if (i = r === null ? null : r.memoizedState, n = e.memoizedState, r === null) {
						if (n === null) {
							if (e.stateNode === null) {
								if (_u) e.stateNode = kp(e.type, e.memoizedProps, t.containerInfo, e);
								else {
									a: {
										t = e.type, n = e.memoizedProps, i = o.ownerDocument || o;
										b: switch (t) {
											case "title":
												r = i.getElementsByTagName("title")[0], (!r || r[Ut] || r[It] || r.namespaceURI === "http://www.w3.org/2000/svg" || r.hasAttribute("itemprop")) && (r = i.createElement(t), i.head.insertBefore(r, i.querySelector("head > title"))), yp(r, t, n), r[It] = e, Xt(r), t = r;
												break a;
											case "link":
												if (o = ah("link", "href", i).get(t + (n.href || ""))) {
													for (s = 0; s < o.length; s++) if (r = o[s], r.getAttribute("href") === (n.href == null || n.href === "" ? null : n.href) && r.getAttribute("rel") === (n.rel == null ? null : n.rel) && r.getAttribute("title") === (n.title == null ? null : n.title) && r.getAttribute("crossorigin") === (n.crossOrigin == null ? null : n.crossOrigin)) {
														o.splice(s, 1);
														break b;
													}
												}
												r = i.createElement(t), yp(r, t, n), i.head.appendChild(r);
												break;
											case "meta":
												if (o = ah("meta", "content", i).get(t + (n.content || ""))) {
													for (s = 0; s < o.length; s++) if (r = o[s], r.getAttribute("content") === (n.content == null ? null : "" + n.content) && r.getAttribute("name") === (n.name == null ? null : n.name) && r.getAttribute("property") === (n.property == null ? null : n.property) && r.getAttribute("http-equiv") === (n.httpEquiv == null ? null : n.httpEquiv) && r.getAttribute("charset") === (n.charSet == null ? null : n.charSet)) {
														o.splice(s, 1);
														break b;
													}
												}
												r = i.createElement(t), yp(r, t, n), i.head.appendChild(r);
												break;
											default: throw Error(a(468, t));
										}
										r[It] = e, Xt(r), t = r;
									}
									e.stateNode = t;
								}
							} else _u || oh(o, e.type, e.stateNode);
						} else e.stateNode = eh(o, n, e.memoizedProps);
					} else i === n ? n === null && e.stateNode !== null && Kl(e, e.memoizedProps, r.memoizedProps) : (i === null ? (t = r.stateNode, t === null || vu || t.parentNode.removeChild(t)) : i.count--, n === null ? _u || oh(o, e.type, e.stateNode) : eh(o, n, e.memoizedProps));
				}
				break;
			case 27:
				Hu(t, e, n), Gu(e), i & 512 && (vu || r === null || zl(r, r.return)), r !== null && i & 4 && Kl(e, e.memoizedProps, r.memoizedProps);
				break;
			case 5:
				if (o = yu, yu = !1, Hu(t, e, n), yu = o, Gu(e), i & 512 && (vu || r === null || zl(r, r.return)), e.flags & 32) {
					t = e.stateNode;
					try {
						wn(t, ""), T = !0;
					} catch (t) {
						kf(e, e.return, t);
					}
				}
				i & 4 && e.stateNode != null && (t = e.memoizedProps, Kl(e, t, r === null ? t : r.memoizedProps)), i & 1024 && (bu = !0);
				break;
			case 6:
				if (Hu(t, e, n), Gu(e), i & 4) {
					if (e.stateNode === null) throw Error(a(162));
					t = e.memoizedProps, n = e.stateNode;
					try {
						n.nodeValue = t, T = !0;
					} catch (t) {
						kf(e, e.return, t);
					}
				}
				break;
			case 3:
				if (T = !1, ih = null, o = Uu, Uu = Im(t.containerInfo), Hu(t, e, n), Uu = o, Gu(e), i & 4 && r !== null && r.memoizedState.isDehydrated) try {
					ng(t.containerInfo);
				} catch (t) {
					kf(e, e.return, t);
				}
				bu && (bu = !1, Ku(e)), T = !1;
				break;
			case 4:
				i = yu, yu = _u, r = sn(), o = Uu, Uu = Im(e.stateNode.containerInfo), Hu(t, e, n), Gu(e), Uu = o, T && wu && (Tu = !0), T = r, yu = i;
				break;
			case 12:
				Hu(t, e, n), Gu(e);
				break;
			case 31:
				Hu(t, e, n), Gu(e), i & 4 && (t = e.updateQueue, t !== null && (e.updateQueue = null, Vu(e, t)));
				break;
			case 13:
				Hu(t, e, n), Gu(e), e.child.flags & 8192 && e.memoizedState !== null != (r !== null && r.memoizedState !== null) && (Ad = et()), i & 4 && (t = e.updateQueue, t !== null && (e.updateQueue = null, Vu(e, t)));
				break;
			case 22:
				o = e.memoizedState !== null, s = r !== null && r.memoizedState !== null;
				var c = _u, l = vu, u = yu;
				_u = c || o, yu = u || o, vu = l || s, Hu(t, e, n), vu = l, yu = u, _u = c, Gu(e), i & 8192 && (t = e.stateNode, t._visibility = o ? t._visibility & -2 : t._visibility | 1, !o || r === null || s || _u || vu || (t = s || vu, n = _u, r = vu, _u = o || _u, vu = t, Xu(e, 2), _u = n, vu = r), !o && yu || Au(e, o)), i & 4 && (t = e.updateQueue, t !== null && (n = t.retryQueue, n !== null && (t.retryQueue = null, Vu(e, n))));
				break;
			case 19:
				Hu(t, e, n), Gu(e), i & 4 && (t = e.updateQueue, t !== null && (e.updateQueue = null, Vu(e, t)));
				break;
			case 30:
				i & 512 && (vu || r === null || zl(r, r.return)), i = sn(), o = wu, s = (n & 335544064) === n, c = e.memoizedProps, wu = s && Ni(c.default, c.update) !== "none", Hu(t, e, n), Gu(e), s && r !== null && T && (e.flags |= 4), wu = o, T = i;
				break;
			case 21: break;
			case 7: i & 512 && (vu || r === null || zl(r, r.return)), r && r.stateNode !== null && (r.stateNode._fragmentFiber = e);
			default: Hu(t, e, n), Gu(e);
		}
	}
	function Gu(e) {
		var t = e.flags;
		if (t & 2) {
			try {
				for (var n, r = e.return; r !== null;) {
					if (ql(r)) {
						n = r;
						break;
					}
					r = r.return;
				}
				r = null;
				for (var i = e.return; i !== null;) {
					if (Wl(i)) {
						var o = i.stateNode;
						r === null ? r = [o] : r.push(o);
					}
					if (Ul(i)) break;
					i = i.return;
				}
				var s = r;
				if (n == null) throw Error(a(160));
				switch (n.tag) {
					case 27:
						var c = n.stateNode;
						Xl(e, Jl(e), c, s);
						break;
					case 5:
						var l = n.stateNode;
						n.flags & 32 && (wn(l, ""), n.flags &= -33), Xl(e, Jl(e), l, s);
						break;
					case 3:
					case 4:
						var u = n.stateNode.containerInfo;
						Yl(e, Jl(e), u, s);
						break;
					default: throw Error(a(161));
				}
			} catch (t) {
				kf(e, e.return, t);
			}
			e.flags &= -3;
		}
		t & 4096 && (e.flags &= -4097);
	}
	function Ku(e) {
		if (e.subtreeFlags & 1024) for (e = e.child; e !== null;) {
			var t = e;
			Ku(t), t.tag === 5 && t.flags & 1024 && (t = t.stateNode, Ah = !0, t.reset(), Ah = !1), e = e.sibling;
		}
	}
	function qu(e, t) {
		if (t.subtreeFlags & 9270) for (t = t.child; t !== null;) Ju(t, e), t = t.sibling;
		else gu(t, !1);
	}
	function Ju(e, t) {
		var n = e.alternate;
		if (n === null) cu(e, !1);
		else switch (e.tag) {
			case 3:
				if (Eu = Cu = !1, nu(), qu(t, e), !Cu && !Tu) {
					if (e = tu, e !== null) for (var r = 0; r < e.length; r += 3) {
						n = e[r];
						var i = e[r + 1];
						Up(n, e[r + 2]), n = n.ownerDocument.documentElement, n !== null && n.animate({
							opacity: [0, 0],
							pointerEvents: ["none", "none"]
						}, {
							duration: 0,
							fill: "forwards",
							pseudoElement: "::view-transition-group(" + i + ")"
						});
					}
					e = t.containerInfo, e = e.nodeType === 9 ? e.documentElement : e.ownerDocument.documentElement, e !== null && e.style.viewTransitionName === "" && (e.style.viewTransitionName = "none", e.animate({
						opacity: [0, 0],
						pointerEvents: ["none", "none"]
					}, {
						duration: 0,
						fill: "forwards",
						pseudoElement: "::view-transition-group(root)"
					}), e.animate({
						width: [0, 0],
						height: [0, 0]
					}, {
						duration: 0,
						fill: "forwards",
						pseudoElement: "::view-transition"
					})), Eu = !0;
				}
				tu = null;
				break;
			case 5:
				qu(t, e);
				break;
			case 4:
				r = Cu, Cu = !1, qu(t, e), Cu && (Tu = !0), Cu = r;
				break;
			case 22:
				e.memoizedState === null && (n.memoizedState === null ? qu(t, e) : cu(e, !1));
				break;
			case 30:
				r = Cu, i = nu(), Cu = !1, qu(t, e), Cu && (e.flags |= 4);
				var a = e.memoizedProps, o = e.stateNode;
				t = ji(a, o), o = ji(n.memoizedProps, o);
				var s = Ni(a.default, a.update);
				s === "none" ? t = !1 : (a = n.memoizedState, n.memoizedState = null, n = e.child, ru = 0, t = hu(e, n, t, o, s, a, !0), ru !== (a === null ? 0 : a.length) && (e.flags |= 32)), e.flags & 4 && t ? (Jd(e, e.memoizedProps.onUpdate), tu = i) : i !== null && (i.push.apply(i, tu), tu = i), Cu = e.flags & 32 ? !0 : r;
				break;
			default: qu(t, e);
		}
	}
	function Yu(e, t) {
		if (t.subtreeFlags & 8772) for (t = t.child; t !== null;) ku(e, t.alternate, t), t = t.sibling;
	}
	function Xu(e, t) {
		for (e = e.child; e !== null;) {
			var n = e, r = t;
			switch (n.tag) {
				case 0:
				case 11:
				case 14:
				case 15:
					Fl(4, n, n.return), Xu(n, r);
					break;
				case 1:
					zl(n, n.return);
					var i = n.stateNode;
					typeof i.componentWillUnmount == "function" && Ll(n, n.return, i), Xu(n, r);
					break;
				case 27: r & 2 && Mm(n.stateNode, n.type, n.memoizedProps);
				case 5:
					zl(n, n.return), n.tag !== 5 && n.tag !== 27 || Hl(n), Xu(n, r);
					break;
				case 6:
					Hl(n);
					break;
				case 26:
					zl(n, n.return), i = n.stateNode, n.memoizedState !== null || i === null || vu || i.parentNode.removeChild(i), Xu(n, r);
					break;
				case 22:
					n.memoizedState === null && Xu(n, r);
					break;
				case 30:
					zl(n, n.return), Xu(n, r);
					break;
				case 7: zl(n, n.return);
				default: Xu(n, r);
			}
			e = e.sibling;
		}
	}
	function Zu(e, t, n) {
		for (n = t.subtreeFlags & 8772 ? n : n & -2, t = t.child; t !== null;) {
			var r = t.alternate, i = e, a = t, o = a.flags, s = !!(n & 1);
			switch (a.tag) {
				case 0:
				case 11:
				case 15:
					Zu(i, a, n), Pl(4, a);
					break;
				case 1:
					if (Zu(i, a, n), r = a, i = r.stateNode, typeof i.componentDidMount == "function") try {
						i.componentDidMount();
					} catch (e) {
						kf(r, r.return, e);
					}
					if (r = a, i = r.updateQueue, i !== null) {
						var c = r.stateNode;
						try {
							var l = i.shared.hiddenCallbacks;
							if (l !== null) for (i.shared.hiddenCallbacks = null, i = 0; i < l.length; i++) Fo(l[i], c);
						} catch (e) {
							kf(r, r.return, e);
						}
					}
					s && o & 64 && Il(a), Rl(a, a.return);
					break;
				case 27: n & 2 && Zl(a);
				case 5:
					a.tag !== 5 && a.tag !== 27 || Vl(a), Zu(i, a, n), s && r === null && o & 4 && Gl(a), Rl(a, a.return);
					break;
				case 6:
					Vl(a);
					break;
				case 26:
					c = a.stateNode, a.memoizedState !== null || c === null || _u || oh(Im(c.ownerDocument), a.type, c), Zu(i, a, n), s && r === null && o & 4 && Gl(a), Rl(a, a.return);
					break;
				case 12:
					Zu(i, a, n);
					break;
				case 31:
					Zu(i, a, n), s && o & 4 && Ru(i, a);
					break;
				case 13:
					Zu(i, a, n), s && o & 4 && zu(i, a);
					break;
				case 22:
					a.memoizedState === null && Zu(i, a, n), Rl(a, a.return);
					break;
				case 30:
					Zu(i, a, n), Rl(a, a.return);
					break;
				case 7: Rl(a, a.return);
				default: Zu(i, a, n);
			}
			t = t.sibling;
		}
	}
	function Qu(e, t) {
		var n = null;
		e !== null && e.memoizedState !== null && e.memoizedState.cachePool !== null && (n = e.memoizedState.cachePool.pool), e = null, t.memoizedState !== null && t.memoizedState.cachePool !== null && (e = t.memoizedState.cachePool.pool), e !== n && (e != null && e.refCount++, n != null && Ga(n));
	}
	function $u(e, t) {
		e = null, t.alternate !== null && (e = t.alternate.memoizedState.cache), t = t.memoizedState.cache, t !== e && (t.refCount++, e != null && Ga(e));
	}
	function ed(e, t, n, r) {
		var i = (n & 335544064) === n;
		if (t.subtreeFlags & (i ? 10262 : 10256)) for (t = t.child; t !== null;) td(e, t, n, r), t = t.sibling;
		else i && mu(t);
	}
	function td(e, t, n, r) {
		var i = (n & 335544064) === n;
		i && t.alternate === null && t.return !== null && t.return.alternate !== null && pu(t);
		var a = t.flags;
		switch (t.tag) {
			case 0:
			case 11:
			case 15:
				ed(e, t, n, r), a & 2048 && Pl(9, t);
				break;
			case 1:
				ed(e, t, n, r);
				break;
			case 3:
				ed(e, t, n, r), i && Eu && (e = e.containerInfo, e = e.nodeType === 9 ? e.body : e.nodeName === "HTML" ? e.ownerDocument.body : e, e.style.viewTransitionName === "root" && (e.style.viewTransitionName = ""), e = e.ownerDocument.documentElement, e !== null && e.style.viewTransitionName === "none" && (e.style.viewTransitionName = "")), a & 2048 && (a = null, t.alternate !== null && (a = t.alternate.memoizedState.cache), t = t.memoizedState.cache, t !== a && (t.refCount++, a != null && Ga(a)));
				break;
			case 12:
				if (a & 2048) {
					ed(e, t, n, r), a = t.stateNode;
					try {
						var o = t.memoizedProps, s = o.id, c = o.onPostCommit;
						typeof c == "function" && c(s, t.alternate === null ? "mount" : "update", a.passiveEffectDuration, -0);
					} catch (e) {
						kf(t, t.return, e);
					}
				} else ed(e, t, n, r);
				break;
			case 31:
				ed(e, t, n, r);
				break;
			case 13:
				ed(e, t, n, r);
				break;
			case 23: break;
			case 22:
				o = t.stateNode, s = t.alternate, t.memoizedState === null ? (i && s !== null && s.memoizedState !== null && pu(t), o._visibility & 2 ? ed(e, t, n, r) : (o._visibility |= 2, nd(e, t, n, r, !!(t.subtreeFlags & 10256) || !1))) : (i && s !== null && s.memoizedState === null && pu(s), o._visibility & 2 ? ed(e, t, n, r) : rd(e, t)), a & 2048 && Qu(s, t);
				break;
			case 24:
				ed(e, t, n, r), a & 2048 && $u(t.alternate, t);
				break;
			case 30:
				i && (a = t.alternate, a !== null && (ou(a.child, !0), ou(t.child, !0))), ed(e, t, n, r);
				break;
			default: ed(e, t, n, r);
		}
	}
	function nd(e, t, n, r, i) {
		for (i &&= !!(t.subtreeFlags & 10256) || !1, t = t.child; t !== null;) {
			var a = e, o = t, s = n, c = r, l = o.flags;
			switch (o.tag) {
				case 0:
				case 11:
				case 15:
					nd(a, o, s, c, i), Pl(8, o);
					break;
				case 23: break;
				case 22:
					var u = o.stateNode;
					o.memoizedState === null ? (u._visibility |= 2, nd(a, o, s, c, i)) : u._visibility & 2 ? nd(a, o, s, c, i) : rd(a, o), i && l & 2048 && Qu(o.alternate, o);
					break;
				case 24:
					nd(a, o, s, c, i), i && l & 2048 && $u(o.alternate, o);
					break;
				default: nd(a, o, s, c, i);
			}
			t = t.sibling;
		}
	}
	function rd(e, t) {
		if (t.subtreeFlags & 10256) for (t = t.child; t !== null;) {
			var n = e, r = t, i = r.flags;
			switch (r.tag) {
				case 22:
					rd(n, r), i & 2048 && Qu(r.alternate, r);
					break;
				case 24:
					rd(n, r), i & 2048 && $u(r.alternate, r);
					break;
				default: rd(n, r);
			}
			t = t.sibling;
		}
	}
	var id = 8192;
	function ad(e, t, n) {
		if (e.subtreeFlags & id) for (e = e.child; e !== null;) od(e, t, n), e = e.sibling;
	}
	function od(e, t, n) {
		switch (e.tag) {
			case 26:
				ad(e, t, n), e.flags & id && (e.memoizedState === null ? (e = e.stateNode, (t & 335544128) === t && dh(n, e)) : fh(n, Uu, e.memoizedState, e.memoizedProps));
				break;
			case 5:
				ad(e, t, n), e.flags & id && (e = e.stateNode, (t & 335544128) === t && dh(n, e));
				break;
			case 3:
			case 4:
				var r = Uu;
				Uu = Im(e.stateNode.containerInfo), ad(e, t, n), Uu = r;
				break;
			case 22:
				e.memoizedState === null && (r = e.alternate, r !== null && r.memoizedState !== null ? (r = id, id = 16777216, ad(e, t, n), id = r) : ad(e, t, n));
				break;
			case 30:
				if ((e.flags & id) !== 0 && (r = e.memoizedProps.name, r != null && r !== "auto")) {
					var i = e.stateNode;
					i.paired = null, $l === null && ($l = /* @__PURE__ */ new Map()), $l.set(r, i);
				}
				ad(e, t, n);
				break;
			default: ad(e, t, n);
		}
	}
	function sd(e) {
		var t = e.alternate;
		if (t !== null && (e = t.child, e !== null)) {
			t.child = null;
			do
				t = e.sibling, e.sibling = null, e = t;
			while (e !== null);
		}
	}
	function cd(e) {
		var t = e.deletions;
		if (e.flags & 16) {
			if (t !== null) for (var n = 0; n < t.length; n++) {
				var r = t[n];
				Su = r, dd(r, e);
			}
			sd(e);
		}
		if (e.subtreeFlags & 10256) for (e = e.child; e !== null;) ld(e), e = e.sibling;
	}
	function ld(e) {
		switch (e.tag) {
			case 0:
			case 11:
			case 15:
				cd(e), e.flags & 2048 && Fl(9, e, e.return);
				break;
			case 3:
				cd(e);
				break;
			case 12:
				cd(e);
				break;
			case 22:
				var t = e.stateNode;
				e.memoizedState !== null && t._visibility & 2 && (e.return === null || e.return.tag !== 13) ? (t._visibility &= -3, ud(e)) : cd(e);
				break;
			default: cd(e);
		}
	}
	function ud(e) {
		var t = e.deletions;
		if (e.flags & 16) {
			if (t !== null) for (var n = 0; n < t.length; n++) {
				var r = t[n];
				Su = r, dd(r, e);
			}
			sd(e);
		}
		for (e = e.child; e !== null;) {
			switch (t = e, t.tag) {
				case 0:
				case 11:
				case 15:
					Fl(8, t, t.return), ud(t);
					break;
				case 22:
					n = t.stateNode, n._visibility & 2 && (n._visibility &= -3, ud(t));
					break;
				default: ud(t);
			}
			e = e.sibling;
		}
	}
	function dd(e, t) {
		for (; Su !== null;) {
			var n = Su;
			switch (n.tag) {
				case 0:
				case 11:
				case 15:
					Fl(8, n, t);
					break;
				case 23:
				case 22:
					if (n.memoizedState !== null && n.memoizedState.cachePool !== null) {
						var r = n.memoizedState.cachePool.pool;
						r != null && r.refCount++;
					}
					break;
				case 24: Ga(n.memoizedState.cache);
			}
			if (r = n.child, r !== null) r.return = n, Su = r;
			else a: for (n = e; Su !== null;) {
				r = Su;
				var i = r.sibling, a = r.return;
				if (Nu(r), r === n) {
					Su = null;
					break a;
				}
				if (i !== null) {
					i.return = a, Su = i;
					break a;
				}
				Su = a;
			}
		}
	}
	var fd = {
		getCacheForType: function(e) {
			var t = za(D), n = t.data.get(e);
			return n === void 0 && (n = e(), t.data.set(e, n)), n;
		},
		cacheSignal: function() {
			return za(D).controller.signal;
		}
	}, pd = typeof WeakMap == "function" ? WeakMap : Map, md = 0, hd = null, M = null, N = 0, gd = 0, P = null, _d = !1, vd = !1, yd = !1, bd = 0, xd = 0, Sd = 0, Cd = 0, wd = 0, Td = 0, Ed = 0, Dd = null, Od = null, kd = !1, Ad = 0, jd = 0, Md = Infinity, Nd = null, F = null, Pd = 0, Fd = null, Id = null, Ld = 0, Rd = 0, zd = null, Bd = null, Vd = null, Hd = null, Ud = null, Wd = 0, Gd = null;
	function Kd() {
		return md & 2 && N !== 0 ? N & -N : C.T === null ? Nt() : Xf();
	}
	function qd() {
		if (Td === 0) {
			if (!(N & 536870912) || E) {
				var e = _t;
				_t <<= 1, !(_t & 3932160) && (_t = 262144), Td = e;
			} else Td = 536870912;
		}
		return e = Ho.current, e !== null && (e.flags |= 32), Td;
	}
	function Jd(e, t) {
		if (t != null) {
			var n = e.stateNode, r = n.ref;
			r === null && (r = n.ref = Zp(ji(e.memoizedProps, n))), Hd === null && (Hd = []), Hd.push(t.bind(null, r));
		}
	}
	function Yd(e, t, n) {
		(e === hd && (gd === 2 || gd === 9) || e.cancelPendingCommit !== null) && (nf(e, 0), $d(e, N, Td, !1)), Et(e, n), md & 2 && e === hd || (e === hd && (!(md & 2) && (Cd |= n), xd === 4 && $d(e, N, Td, !1)), Hf(e));
	}
	function Xd(e, t, n) {
		if (md & 6) throw Error(a(327));
		var r = !n && !(t & 127) && (t & e.expiredLanes) === 0 || xt(e, t), i = r ? df(e, t) : lf(e, t, !0), o = r;
		do {
			if (i === 0) {
				vd && !r && $d(e, t, 0, !1);
				break;
			}
			if (n = e.current.alternate, o && !Qd(n)) i = lf(e, t, !1), o = !1;
			else {
				if (i === 2) {
					if (o = t, e.errorRecoveryDisabledLanes & o) var s = 0;
					else s = e.pendingLanes & -536870913, s = s === 0 ? s & 536870912 ? 536870912 : 0 : s;
					if (s !== 0) {
						t = s;
						a: {
							var c = e;
							i = Dd;
							var l = c.current.memoizedState.isDehydrated;
							if (l && (nf(c, s).flags |= 256), s = lf(c, s, !1), s !== 2 && s !== 6) {
								if (yd && !l) {
									c.errorRecoveryDisabledLanes |= o, Cd |= o, i = 4;
									break a;
								}
								o = Od, Od = i, o !== null && (Od === null ? Od = o : Od.push.apply(Od, o));
							}
							i = s;
						}
						if (o = !1, i !== 2) continue;
					}
				}
				if (i === 1) {
					nf(e, 0), $d(e, t, 0, !0);
					break;
				}
				a: {
					switch (r = e, o = i, o) {
						case 0:
						case 1: throw Error(a(345));
						case 4: if ((t & 4194048) !== t && (t & 62914560) !== t) break;
						case 6:
							$d(r, t, Td, !_d);
							break a;
						case 2:
							Od = null;
							break;
						case 3:
						case 5: break;
						default: throw Error(a(329));
					}
					if ((t & 62914560) === t && (i = Ad + 300 - et(), 10 < i)) {
						if ($d(r, t, Td, !_d), bt(r, 0, !0) !== 0) break a;
						Ld = t, r.timeoutHandle = Np(Zd.bind(null, r, n, Od, Nd, kd, t, Td, Cd, Ed, _d, o, "Throttled", -0, 0), i);
						break a;
					}
					Zd(r, n, Od, Nd, kd, t, Td, Cd, Ed, _d, o, null, -0, 0);
				}
				break;
			}
		} while (1);
		Hf(e);
	}
	function Zd(e, t, n, r, i, a, o, s, c, l, u, d, f, p) {
		e.timeoutHandle = -1;
		var m = t.subtreeFlags, h = (a & 335544064) === a;
		d = null, (h || m & 8192 || (m & 16785408) == 16785408) && (d = {
			stylesheets: null,
			count: 0,
			imgCount: 0,
			imgBytes: 0,
			suspenseyImages: [],
			waitingForImages: !0,
			waitingForViewTransition: !1,
			unsuspend: Mn
		}, $l = null, od(t, a, d), h && (m = d, h = e.containerInfo, h = (h.nodeType === 9 ? h : h.ownerDocument).__reactViewTransition, h != null && (m.count++, m.waitingForViewTransition = !0, m = gh.bind(m), h.finished.then(m, m))), m = (a & 62914560) === a ? Ad - et() : (a & 4194048) === a ? jd - et() : 0, m = mh(d, m), m !== null) ? (Ld = a, e.cancelPendingCommit = m(vf.bind(null, e, t, a, n, r, i, o, s, c, l, u, d, null, f, p)), $d(e, a, o, !l)) : vf(e, t, a, n, r, i, o, s, c, l, u, d);
	}
	function Qd(e) {
		for (var t = e;;) {
			var n = t.tag;
			if ((n === 0 || n === 11 || n === 15) && t.flags & 16384 && (n = t.updateQueue, n !== null && (n = n.stores, n !== null))) for (var r = 0; r < n.length; r++) {
				var i = n[r], a = i.getSnapshot;
				i = i.value;
				try {
					if (!ti(a(), i)) return !1;
				} catch {
					return !1;
				}
			}
			if (n = t.child, t.subtreeFlags & 16384 && n !== null) n.return = t, t = n;
			else {
				if (t === e) break;
				for (; t.sibling === null;) {
					if (t.return === null || t.return === e) return !0;
					t = t.return;
				}
				t.sibling.return = t.return, t = t.sibling;
			}
		}
		return !0;
	}
	function $d(e, t, n, r) {
		t = St(e, t), t &= ~wd, t &= ~Cd, e.suspendedLanes |= t, e.pingedLanes &= ~t, r && (e.warmLanes |= t), r = e.expirationTimes;
		for (var i = t; 0 < i;) {
			var a = 31 - ft(i), o = 1 << a;
			r[a] = -1, i &= ~o;
		}
		n !== 0 && Ot(e, n, t);
	}
	function ef() {
		return md & 6 ? !0 : (Uf(0, !1), !1);
	}
	function tf() {
		if (M !== null) {
			if (gd === 0) var e = M.return;
			else e = M, ja = Aa = null, _s(e), _o = null, vo = 0, e = M;
			for (; e !== null;) Nl(e.alternate, e), e = e.return;
			M = null;
		}
	}
	function nf(e, t) {
		var n = e.timeoutHandle;
		return n !== -1 && (e.timeoutHandle = -1, Pp(n)), n = e.cancelPendingCommit, n !== null && (e.cancelPendingCommit = null, n()), Ld = 0, tf(), hd = e, M = n = Ji(e.current, null), N = t, gd = 0, P = null, _d = !1, vd = xt(e, t), yd = !1, Ed = Td = wd = Cd = Sd = xd = 0, Od = Dd = null, kd = !1, bd = St(e, t), Ri(), n;
	}
	function rf(e, t) {
		A = null, C.H = Dc, t === oo || t === co ? (t = ho(), gd = 3) : t === so ? (t = ho(), gd = 4) : gd = t === Gc ? 8 : typeof t == "object" && t && typeof t.then == "function" ? 6 : 1, P = t, M === null && (xd = 1, zc(e, na(t, e.current)));
	}
	function af() {
		var e = Ho.current;
		return e === null ? !0 : (N & 4194048) === N ? Uo === null : (N & 62914560) === N || N & 536870912 ? e === Uo : !1;
	}
	function of() {
		var e = C.H;
		return C.H = Dc, e === null ? Dc : e;
	}
	function sf() {
		var e = C.A;
		return C.A = fd, e;
	}
	function cf() {
		xd = 4, _d || (N & 4194048) !== N && Ho.current !== null || (vd = !0), !(Sd & 134217727) && !(Cd & 134217727) || hd === null || $d(hd, N, Td, !1);
	}
	function lf(e, t, n) {
		var r = md;
		md |= 2;
		var i = of(), a = sf();
		(hd !== e || N !== t) && (Nd = null, nf(e, t)), t = !1;
		var o = xd;
		a: do
			try {
				if (gd !== 0 && M !== null) {
					var s = M, c = P;
					switch (gd) {
						case 8:
							tf(), o = 6;
							break a;
						case 3:
						case 2:
						case 9:
						case 6:
							Ho.current === null && (t = !0);
							var l = gd;
							if (gd = 0, P = null, hf(e, s, c, l), n && vd) {
								o = 0;
								break a;
							}
							break;
						default: l = gd, gd = 0, P = null, hf(e, s, c, l);
					}
				}
				uf(), o = xd;
				break;
			} catch (t) {
				rf(e, t);
			}
		while (1);
		return t && e.shellSuspendCounter++, ja = Aa = null, md = r, C.H = i, C.A = a, M === null && (hd = null, N = 0, Ri()), o;
	}
	function uf() {
		for (; M !== null;) pf(M);
	}
	function df(e, t) {
		var n = md;
		md |= 2;
		var r = of(), i = sf();
		hd !== e || N !== t ? (Nd = null, Md = et() + 500, nf(e, t)) : vd = xt(e, t);
		a: do
			try {
				if (gd !== 0 && M !== null) {
					t = M;
					var o = P;
					b: switch (gd) {
						case 1:
							gd = 0, P = null, hf(e, t, o, 1);
							break;
						case 2:
						case 9:
							if (uo(o)) {
								gd = 0, P = null, mf(t);
								break;
							}
							t = function() {
								gd !== 2 && gd !== 9 || hd !== e || (gd = 7), Hf(e);
							}, o.then(t, t);
							break a;
						case 3:
							gd = 7;
							break a;
						case 4:
							gd = 5;
							break a;
						case 7:
							uo(o) ? (gd = 0, P = null, mf(t)) : (gd = 0, P = null, hf(e, t, o, 7));
							break;
						case 5:
							var s = null;
							switch (M.tag) {
								case 26: s = M.memoizedState;
								case 5:
								case 27:
									var c = M;
									if (s ? lh(s) : c.stateNode.complete) {
										gd = 0, P = null;
										var l = c.sibling;
										if (l !== null) M = l;
										else {
											var u = c.return;
											u === null ? M = null : (M = u, gf(u));
										}
										break b;
									}
							}
							gd = 0, P = null, hf(e, t, o, 5);
							break;
						case 6:
							gd = 0, P = null, hf(e, t, o, 6);
							break;
						case 8:
							tf(), xd = 6;
							break a;
						default: throw Error(a(462));
					}
				}
				ff();
				break;
			} catch (t) {
				rf(e, t);
			}
		while (1);
		return ja = Aa = null, C.H = r, C.A = i, md = n, M === null ? (hd = null, N = 0, Ri(), xd) : 0;
	}
	function ff() {
		for (; M !== null && !Qe();) pf(M);
	}
	function pf(e) {
		var t = Tl(e.alternate, e, bd);
		e.memoizedProps = e.pendingProps, t === null ? gf(e) : M = t;
	}
	function mf(e) {
		var t = e, n = t.alternate;
		switch (t.tag) {
			case 15:
			case 0:
				t = al(n, t, t.pendingProps, t.type, void 0, N);
				break;
			case 11:
				t = al(n, t, t.pendingProps, t.type.render, t.ref, N);
				break;
			case 5:
				_s(t);
				var r = t;
				r === _a && (E ? (wa(r), r.tag === 5 && r.stateNode != null && (va = r.stateNode)) : (wa(r), E = !0));
			default: Nl(n, t), t = M = Yi(t, bd), t = Tl(n, t, bd);
		}
		e.memoizedProps = e.pendingProps, t === null ? gf(e) : M = t;
	}
	function hf(e, t, n, r) {
		ja = Aa = null, _s(t), _o = null, vo = 0;
		var i = t.return;
		try {
			if (Wc(e, i, t, n, N)) {
				xd = 1, zc(e, na(n, e.current)), M = null;
				return;
			}
		} catch (t) {
			if (i !== null) throw M = i, t;
			xd = 1, zc(e, na(n, e.current)), M = null;
			return;
		}
		t.flags & 32768 ? (E || r === 1 ? e = !0 : vd || N & 536870912 ? e = !1 : (_d = e = !0, (r === 2 || r === 9 || r === 3 || r === 6) && (r = Ho.current, r !== null && r.tag === 13 && (r.flags |= 16384))), _f(t, e)) : gf(t);
	}
	function gf(e) {
		var t = e;
		do {
			if (t.flags & 32768) {
				_f(t, _d);
				return;
			}
			e = t.return;
			var n = jl(t.alternate, t, bd);
			if (n !== null) {
				M = n;
				return;
			}
			if (t = t.sibling, t !== null) {
				M = t;
				return;
			}
			M = t = e;
		} while (t !== null);
		xd === 0 && (xd = 5);
	}
	function _f(e, t) {
		do {
			var n = Ml(e.alternate, e);
			if (n !== null) {
				n.flags &= 32767, M = n;
				return;
			}
			if (n = e.return, n !== null && (n.flags |= 32768, n.subtreeFlags = 0, n.deletions = null), !t && (e = e.sibling, e !== null)) {
				M = e;
				return;
			}
			M = e = n;
		} while (e !== null);
		xd = 6, M = null;
	}
	function vf(e, t, n, r, i, o, s, c, l, u, d, f) {
		e.cancelPendingCommit = null;
		do
			Ef();
		while (Pd !== 0);
		if (md & 6) throw Error(a(327));
		if (t !== null) {
			if (t === e.current) throw Error(a(177));
			e === hd && (M = hd = null, N = 0), Id = t, Fd = e, Ld = n, zd = i, Bd = r, yf(e, t, n, s, c, l, f);
		}
	}
	function yf(e, t, n, r, i, a, o) {
		var s = t.lanes | t.childLanes;
		if (Rd = s, s |= Li, Dt(e, n, s, r, i, a), Hd = null, (n & 335544064) === n ? (Ud = Ja(e), r = 10262) : (Ud = null, r = 10256), (t.subtreeFlags & r) !== 0 || (t.flags & r) !== 0 ? (e.callbackNode = null, e.callbackPriority = 0, Ff(it, function() {
			return Df(), null;
		})) : (e.callbackNode = null, e.callbackPriority = 0), Ql = !1, r = !!(t.flags & 13878), t.subtreeFlags & 13878 || r) {
			r = C.T, C.T = null, i = Oe.p, Oe.p = 2, a = md, md |= 4;
			try {
				Du(e, t, n);
			} finally {
				md = a, Oe.p = i, C.T = r;
			}
		}
		Pd = 1, Ql ? Vd = Yp(o, e.containerInfo, Ud, Sf, Cf, xf, wf, Df, bf, null, null) : (Sf(), Cf(), wf());
	}
	function bf(e) {
		if (Pd !== 0) {
			var t = Fd.onRecoverableError;
			t(e, { componentStack: null });
		}
	}
	function xf() {
		Pd === 3 && (Pd = 0, Ju(Id, Fd), Pd = 4);
	}
	function Sf() {
		if (Pd === 1) {
			Pd = 0;
			var e = Fd, t = Id, n = Ld, r = !!(t.flags & 13878);
			if (t.subtreeFlags & 13878 || r) {
				r = C.T, C.T = null;
				var i = Oe.p;
				Oe.p = 2;
				var a = md;
				md |= 4;
				try {
					wu = Tu = !1, Wu(t, e, n), n = Tp;
					var o = si(e.containerInfo), s = n.focusedElem, c = n.selectionRange;
					if (o !== s && s && s.ownerDocument && oi(s.ownerDocument.documentElement, s)) {
						if (c !== null && ci(s)) {
							var l = c.start, u = c.end;
							if (u === void 0 && (u = l), "selectionStart" in s) s.selectionStart = l, s.selectionEnd = Math.min(u, s.value.length);
							else {
								var d = s.ownerDocument || document, f = d && d.defaultView || window;
								if (f.getSelection) {
									var p = f.getSelection(), m = s.textContent.length, h = Math.min(c.start, m), g = c.end === void 0 ? h : Math.min(c.end, m);
									!p.extend && h > g && (o = g, g = h, h = o);
									var _ = ai(s, h), v = ai(s, g);
									if (_ && v && (p.rangeCount !== 1 || p.anchorNode !== _.node || p.anchorOffset !== _.offset || p.focusNode !== v.node || p.focusOffset !== v.offset)) {
										var y = d.createRange();
										y.setStart(_.node, _.offset), p.removeAllRanges(), h > g ? (p.addRange(y), p.extend(v.node, v.offset)) : (y.setEnd(v.node, v.offset), p.addRange(y));
									}
								}
							}
						}
						for (d = [], p = s; p = p.parentNode;) p.nodeType === 1 && d.push({
							element: p,
							left: p.scrollLeft,
							top: p.scrollTop
						});
						for (typeof s.focus == "function" && s.focus(), s = 0; s < d.length; s++) {
							var b = d[s];
							b.element.scrollLeft = b.left, b.element.scrollTop = b.top;
						}
					}
					Ah = !!wp, Tp = wp = null;
				} finally {
					md = a, Oe.p = i, C.T = r;
				}
			}
			e.current = t, Pd = 2;
		}
	}
	function Cf() {
		if (Pd === 2) {
			Pd = 0;
			var e = Fd, t = Id, n = !!(t.flags & 8772);
			if (t.subtreeFlags & 8772 || n) {
				n = C.T, C.T = null;
				var r = Oe.p;
				Oe.p = 2;
				var i = md;
				md |= 4;
				try {
					ku(e, t.alternate, t);
				} finally {
					md = i, Oe.p = r, C.T = n;
				}
			}
			Pd = 3;
		}
	}
	function wf() {
		if (Pd === 4 || Pd === 3) {
			Pd = 0;
			var e = Vd;
			Vd = null, $e();
			var t = Fd, n = Id, r = Ld, i = Bd, a = (r & 335544064) === r ? 10262 : 10256;
			if ((n.subtreeFlags & a) !== 0 || (n.flags & a) !== 0 ? Pd = 5 : (Pd = 0, Id = Fd = null, Tf(t, t.pendingLanes)), a = t.pendingLanes, a === 0 && (F = null), Mt(r), n = n.stateNode, ut && typeof ut.onCommitFiberRoot == "function") try {
				ut.onCommitFiberRoot(lt, n, void 0, (n.current.flags & 128) == 128);
			} catch {}
			if (i !== null) {
				n = C.T, a = Oe.p, Oe.p = 2, C.T = null;
				try {
					for (var o = t.onRecoverableError, s = 0; s < i.length; s++) {
						var c = i[s];
						o(c.value, { componentStack: c.stack });
					}
				} finally {
					C.T = n, Oe.p = a;
				}
			}
			if (i = Hd, o = Ud, Ud = null, i !== null && (Hd = null, o === null && (o = []), e !== null)) for (c = 0; c < i.length; c++) n = (0, i[c])(o), n !== void 0 && e.finished.finally(n);
			Ld & 3 && Ef(), Hf(t), a = t.pendingLanes, r & 261930 && a & 42 ? t === Gd ? Wd++ : (Wd = 0, Gd = t) : (Wd = 0, Gd = null), Uf(0, !1);
		}
	}
	function Tf(e, t) {
		(e.pooledCacheLanes &= t) === 0 && (t = e.pooledCache, t != null && (e.pooledCache = null, Ga(t)));
	}
	function Ef() {
		return Vd !== null && (Vd.skipTransition(), Vd = null), Sf(), Cf(), wf(), Df();
	}
	function Df() {
		if (Pd !== 5) return !1;
		var e = Fd, t = Rd;
		Rd = 0;
		var n = Mt(Ld), r = C.T, i = Oe.p;
		try {
			Oe.p = 32 > n ? 32 : n, C.T = null, n = zd, zd = null;
			var o = Fd, s = Ld;
			if (Pd = 0, Id = Fd = null, Ld = 0, md & 6) throw Error(a(331));
			var c = md;
			if (md |= 4, ld(o.current), td(o, o.current, s, n), md = c, Uf(0, !1), ut && typeof ut.onPostCommitFiberRoot == "function") try {
				ut.onPostCommitFiberRoot(lt, o);
			} catch {}
			return !0;
		} finally {
			Oe.p = i, C.T = r, Tf(e, t);
		}
	}
	function Of(e, t, n) {
		t = na(n, t), t = Vc(e.stateNode, t, 2), e = ko(e, t, 2), e !== null && (Et(e, 2), Hf(e));
	}
	function kf(e, t, n) {
		if (e.tag === 3) Of(e, e, n);
		else for (; t !== null;) {
			if (t.tag === 3) {
				Of(t, e, n);
				break;
			}
			if (t.tag === 1) {
				var r = t.stateNode;
				if (typeof t.type.getDerivedStateFromError == "function" || typeof r.componentDidCatch == "function" && (F === null || !F.has(r))) {
					e = na(n, e), n = Hc(2), r = ko(t, n, 2), r !== null && (Uc(n, r, t, e), Et(r, 2), Hf(r));
					break;
				}
			}
			t = t.return;
		}
	}
	function Af(e, t, n) {
		var r = e.pingCache;
		if (r === null) {
			r = e.pingCache = new pd();
			var i = /* @__PURE__ */ new Set();
			r.set(t, i);
		} else i = r.get(t), i === void 0 && (i = /* @__PURE__ */ new Set(), r.set(t, i));
		i.has(n) || (yd = !0, i.add(n), e = jf.bind(null, e, t, n), t.then(e, e));
	}
	function jf(e, t, n) {
		var r = e.pingCache;
		r !== null && r.delete(t), e.pingedLanes |= e.suspendedLanes & n, e.warmLanes &= ~n, hd === e && (N & n) === n && (xd === 4 || xd === 3 && (N & 62914560) === N && 300 > et() - Ad ? md & 2 ? wd |= n : nf(e, 0) : wd |= n, Ed === N && (Ed = 0)), Hf(e);
	}
	function Mf(e, t) {
		t === 0 && (t = wt()), e = Vi(e, t), e !== null && (Et(e, t), Hf(e));
	}
	function Nf(e) {
		var t = e.memoizedState, n = 0;
		t !== null && (n = t.retryLane), Mf(e, n);
	}
	function Pf(e, t) {
		var n = 0;
		switch (e.tag) {
			case 31:
			case 13:
				var r = e.stateNode, i = e.memoizedState;
				i !== null && (n = i.retryLane);
				break;
			case 19:
				r = e.stateNode;
				break;
			case 22:
				r = e.stateNode._retryCache;
				break;
			default: throw Error(a(314));
		}
		r !== null && r.delete(t), Mf(e, n);
	}
	function Ff(e, t) {
		return Xe(e, t);
	}
	var If = null, Lf = null, Rf = !1, zf = !1, Bf = !1, Vf = 0;
	function Hf(e) {
		e !== Lf && e.next === null && (Lf === null ? If = Lf = e : Lf = Lf.next = e), zf = !0, Rf || (Rf = !0, Yf());
	}
	function Uf(e, t) {
		if (!Bf && zf) {
			Bf = !0;
			do
				for (var n = !1, r = If; r !== null;) {
					if (!t) {
						if (e !== 0) {
							var i = r.pendingLanes;
							if (i === 0) var a = 0;
							else {
								var o = r.suspendedLanes, s = r.pingedLanes;
								a = (1 << 31 - ft(42 | e) + 1) - 1, a &= i & ~(o & ~s), a = a & 201326741 ? a & 201326741 | 1 : a ? a | 2 : 0;
							}
							a !== 0 && (n = !0, Jf(r, a));
						} else a = N, a = bt(r, r === hd ? a : 0, r.cancelPendingCommit !== null || r.timeoutHandle !== -1), !(a & 3) || xt(r, a) || (n = !0, Jf(r, a));
					}
					r = r.next;
				}
			while (n);
			Bf = !1;
		}
	}
	function Wf() {
		Gf();
	}
	function Gf() {
		zf = Rf = !1;
		var e = 0;
		Vf !== 0 && Mp() && (e = Vf);
		for (var t = et(), n = null, r = If; r !== null;) {
			var i = r.next, a = Kf(r, t);
			a === 0 ? (r.next = null, n === null ? If = i : n.next = i, i === null && (Lf = n)) : (n = r, (e !== 0 || a & 3) && (zf = !0)), r = i;
		}
		Pd !== 0 && Pd !== 5 || Uf(e, !1), Vf !== 0 && (Vf = 0);
	}
	function Kf(e, t) {
		for (var n = e.suspendedLanes, r = e.pingedLanes, i = e.expirationTimes, a = e.pendingLanes & -62914561; 0 < a;) {
			var o = 31 - ft(a), s = 1 << o, c = i[o];
			c === -1 ? ((s & n) === 0 || (s & r) !== 0) && (i[o] = Ct(s, t)) : c <= t && (e.expiredLanes |= s), a &= ~s;
		}
		if (t = hd, n = N, n = bt(e, e === t ? n : 0, e.cancelPendingCommit !== null || e.timeoutHandle !== -1), r = e.callbackNode, n === 0 || e === t && (gd === 2 || gd === 9) || e.cancelPendingCommit !== null) return r !== null && r !== null && Ze(r), e.callbackNode = null, e.callbackPriority = 0;
		if (!(n & 3) || xt(e, n)) {
			if (t = n & -n, t === e.callbackPriority) return t;
			switch (r !== null && Ze(r), Mt(n)) {
				case 2:
				case 8:
					n = rt;
					break;
				case 32:
					n = it;
					break;
				case 268435456:
					n = ot;
					break;
				default: n = it;
			}
			return r = qf.bind(null, e), n = Xe(n, r), e.callbackPriority = t, e.callbackNode = n, t;
		}
		return r !== null && r !== null && Ze(r), e.callbackPriority = 2, e.callbackNode = null, 2;
	}
	function qf(e, t) {
		if (Pd !== 0 && Pd !== 5) return e.callbackNode = null, e.callbackPriority = 0, null;
		var n = e.callbackNode;
		if (Ef() && e.callbackNode !== n) return null;
		var r = N;
		return r = bt(e, e === hd ? r : 0, e.cancelPendingCommit !== null || e.timeoutHandle !== -1), r === 0 ? null : (Xd(e, r, t), Kf(e, et()), e.callbackNode != null && e.callbackNode === n ? qf.bind(null, e) : null);
	}
	function Jf(e, t) {
		if (Ef()) return null;
		Xd(e, t, !0);
	}
	function Yf() {
		Lp(function() {
			md & 6 ? Xe(nt, Wf) : Gf();
		});
	}
	function Xf() {
		if (Vf === 0) {
			var e = Za;
			e === 0 && (e = gt, gt <<= 1, !(gt & 261888) && (gt = 256)), Vf = e;
		}
		return Vf;
	}
	function Zf(e) {
		return e == null || typeof e == "symbol" || typeof e == "boolean" ? null : typeof e == "function" ? e : jn(e);
	}
	function Qf(e, t, n, r, i) {
		if (t === "submit" && n && n.stateNode === i) {
			var a = Zf((i[Lt] || null).action), o = r.submitter;
			o && (t = (t = o[Lt] || null) ? Zf(t.formAction) : o.getAttribute("formAction"), t !== null && (a = t, o = null));
			var s = new $n("action", "action", null, r, i);
			e.push({
				event: s,
				listeners: [{
					instance: null,
					listener: function() {
						if (r.defaultPrevented) {
							if (Vf !== 0) {
								var e = new FormData(i, o);
								pc(n, {
									pending: !0,
									data: e,
									method: i.method,
									action: a
								}, null, e);
							}
						} else typeof a == "function" && (s.preventDefault(), e = new FormData(i, o), pc(n, {
							pending: !0,
							data: e,
							method: i.method,
							action: a
						}, a, e));
					},
					currentTarget: i
				}]
			});
		}
	}
	for (var $f = 0; $f < Oi.length; $f++) {
		var ep = Oi[$f];
		ki(ep.toLowerCase(), "on" + (ep[0].toUpperCase() + ep.slice(1)));
	}
	ki(bi, "onAnimationEnd"), ki(xi, "onAnimationIteration"), ki(Si, "onAnimationStart"), ki("dblclick", "onDoubleClick"), ki("focusin", "onFocus"), ki("focusout", "onBlur"), ki(Ci, "onTransitionRun"), ki(wi, "onTransitionStart"), ki(Ti, "onTransitionCancel"), ki(Ei, "onTransitionEnd"), tn("onMouseEnter", ["mouseout", "mouseover"]), tn("onMouseLeave", ["mouseout", "mouseover"]), tn("onPointerEnter", ["pointerout", "pointerover"]), tn("onPointerLeave", ["pointerout", "pointerover"]), en("onChange", "change click focusin focusout input keydown keyup selectionchange".split(" ")), en("onSelect", "focusout contextmenu dragend focusin keydown keyup mousedown mouseup selectionchange".split(" ")), en("onBeforeInput", [
		"compositionend",
		"keypress",
		"textInput",
		"paste"
	]), en("onCompositionEnd", "compositionend focusout keydown keypress keyup mousedown".split(" ")), en("onCompositionStart", "compositionstart focusout keydown keypress keyup mousedown".split(" ")), en("onCompositionUpdate", "compositionupdate focusout keydown keypress keyup mousedown".split(" "));
	var tp = "abort canplay canplaythrough durationchange emptied encrypted ended error loadeddata loadedmetadata loadstart pause play playing progress ratechange resize seeked seeking stalled suspend timeupdate volumechange waiting".split(" "), np = new Set("beforetoggle cancel close invalid load scroll scrollend toggle".split(" ").concat(tp));
	function rp(e, t) {
		t = !!(t & 4);
		for (var n = 0; n < e.length; n++) {
			var r = e[n], i = r.event;
			r = r.listeners;
			a: {
				var a = void 0;
				if (t) for (var o = r.length - 1; 0 <= o; o--) {
					var s = r[o], c = s.instance, l = s.currentTarget;
					if (s = s.listener, c !== a && i.isPropagationStopped()) break a;
					a = s, i.currentTarget = l;
					try {
						a(i);
					} catch (e) {
						Pi(e);
					}
					i.currentTarget = null, a = c;
				}
				else for (o = 0; o < r.length; o++) {
					if (s = r[o], c = s.instance, l = s.currentTarget, s = s.listener, c !== a && i.isPropagationStopped()) break a;
					a = s, i.currentTarget = l;
					try {
						a(i);
					} catch (e) {
						Pi(e);
					}
					i.currentTarget = null, a = c;
				}
			}
		}
	}
	function I(e, t) {
		var n = t[zt];
		n === void 0 && (n = t[zt] = /* @__PURE__ */ new Set());
		var r = e + "__bubble";
		n.has(r) || (sp(t, e, 2, !1), n.add(r));
	}
	function ip(e, t, n) {
		var r = 0;
		t && (r |= 4), sp(n, e, r, t);
	}
	var ap = "_reactListening" + Math.random().toString(36).slice(2);
	function op(e) {
		if (!e[ap]) {
			e[ap] = !0, Qt.forEach(function(t) {
				t !== "selectionchange" && (np.has(t) || ip(t, !1, e), ip(t, !0, e));
			});
			var t = e.nodeType === 9 ? e : e.ownerDocument;
			t === null || t[ap] || (t[ap] = !0, ip("selectionchange", !1, t));
		}
	}
	function sp(e, t, n, r) {
		switch (Lh(t)) {
			case 2:
				var i = jh;
				break;
			case 8:
				i = Mh;
				break;
			default: i = Nh;
		}
		n = i.bind(null, t, n, e), i = void 0, !Hn || t !== "touchstart" && t !== "touchmove" && t !== "wheel" || (i = !0), r ? i === void 0 ? e.addEventListener(t, n, !0) : e.addEventListener(t, n, {
			capture: !0,
			passive: i
		}) : i === void 0 ? e.addEventListener(t, n, !1) : e.addEventListener(t, n, { passive: i });
	}
	function cp(e, t, n, r, i) {
		var a = r;
		if (!(t & 1) && !(t & 2) && r !== null) a: for (;;) {
			if (r === null) return;
			var o = r.tag;
			if (o === 3 || o === 4) {
				var s = r.stateNode.containerInfo;
				if (s === i) break;
				if (o === 4) for (o = r.return; o !== null;) {
					var l = o.tag;
					if ((l === 3 || l === 4) && o.stateNode.containerInfo === i) return;
					o = o.return;
				}
				for (; s !== null;) {
					if (o = Kt(s), o === null) return;
					if (l = o.tag, l === 5 || l === 6 || l === 26 || l === 27) {
						r = a = o;
						continue a;
					}
					s = s.parentNode;
				}
			}
			r = r.return;
		}
		zn(function() {
			var r = a, i = Pn(n), o = [];
			a: {
				var s = Di.get(e);
				if (s !== void 0) {
					var l = $n, u = e;
					switch (e) {
						case "keypress": if (Jn(n) === 0) break a;
						case "keydown":
						case "keyup":
							l = _r;
							break;
						case "focusin":
							u = "focus", l = cr;
							break;
						case "focusout":
							u = "blur", l = cr;
							break;
						case "beforeblur":
						case "afterblur":
							l = cr;
							break;
						case "click": if (n.button === 2) break a;
						case "auxclick":
						case "dblclick":
						case "mousedown":
						case "mousemove":
						case "mouseup":
						case "mouseout":
						case "mouseover":
						case "contextmenu":
							l = or;
							break;
						case "drag":
						case "dragend":
						case "dragenter":
						case "dragexit":
						case "dragleave":
						case "dragover":
						case "dragstart":
						case "drop":
							l = sr;
							break;
						case "touchcancel":
						case "touchend":
						case "touchmove":
						case "touchstart":
							l = br;
							break;
						case bi:
						case xi:
						case Si:
							l = lr;
							break;
						case Ei:
							l = xr;
							break;
						case "scroll":
						case "scrollend":
							l = tr;
							break;
						case "wheel":
							l = Sr;
							break;
						case "copy":
						case "cut":
						case "paste":
							l = ur;
							break;
						case "gotpointercapture":
						case "lostpointercapture":
						case "pointercancel":
						case "pointerdown":
						case "pointermove":
						case "pointerout":
						case "pointerover":
						case "pointerup":
							l = vr;
							break;
						case "submit":
							l = yr;
							break;
						case "toggle":
						case "beforetoggle": l = Cr;
					}
					var d = !!(t & 4), f = !d && (e === "scroll" || e === "scrollend"), p = d ? s === null ? null : s + "Capture" : s;
					d = [];
					for (var m = r, h; m !== null;) {
						var g = m;
						if (h = g.stateNode, g = g.tag, g !== 5 && g !== 26 && g !== 27 || h === null || p === null || (g = Bn(m, p), g != null && d.push(lp(m, g, h))), f) break;
						m = m.return;
					}
					0 < d.length && (s = new l(s, u, null, n, i), o.push({
						event: s,
						listeners: d
					}));
				}
			}
			if (!(t & 7)) {
				a: {
					if (l = e === "mouseover" || e === "pointerover", s = e === "mouseout" || e === "pointerout", l && n !== Nn && (u = n.relatedTarget || n.fromElement) && (Kt(u) || u[Rt])) break a;
					(s || l) && (u = i.window === i ? i : (l = i.ownerDocument) ? l.defaultView || l.parentWindow : window, s ? (l = n.relatedTarget || n.toElement, s = r, l = l ? Kt(l) : null, l !== null && (f = c(l), d = l.tag, l !== f || d !== 5 && d !== 27 && d !== 6) && (l = null)) : (s = null, l = r), s !== l && (d = or, g = "onMouseLeave", p = "onMouseEnter", m = "mouse", (e === "pointerout" || e === "pointerover") && (d = vr, g = "onPointerLeave", p = "onPointerEnter", m = "pointer"), f = s == null ? u : Jt(s), h = l == null ? u : Jt(l), u = new d(g, m + "leave", s, n, i), u.target = f, u.relatedTarget = h, g = null, Kt(i) === r && (d = new d(p, m + "enter", l, n, i), d.target = h, d.relatedTarget = f, g = d), f = g, d = s && l ? ae(s, l, dp) : null, s !== null && fp(o, u, s, d, !1), l !== null && f !== null && fp(o, f, l, d, !0)));
				}
				a: {
					if (s = r ? Jt(r) : window, l = s.nodeName && s.nodeName.toLowerCase(), l === "select" || l === "input" && s.type === "file") var _ = Ur;
					else if (Lr(s)) {
						if (Wr) _ = $r;
						else {
							_ = Zr;
							var v = Xr;
						}
					} else l = s.nodeName, !l || l.toLowerCase() !== "input" || s.type !== "checkbox" && s.type !== "radio" ? r && On(r.elementType) && (_ = Ur) : _ = Qr;
					if (_ &&= _(e, r)) {
						Rr(o, _, n, i);
						break a;
					}
					v && v(e, s, r);
				}
				switch (v = r ? Jt(r) : window, e) {
					case "focusin":
						(Lr(v) || v.contentEditable === "true") && (ui = v, di = r, fi = null);
						break;
					case "focusout":
						fi = di = ui = null;
						break;
					case "mousedown":
						pi = !0;
						break;
					case "contextmenu":
					case "mouseup":
					case "dragend":
						pi = !1, mi(o, n, i);
						break;
					case "selectionchange": if (li) break;
					case "keydown":
					case "keyup": mi(o, n, i);
				}
				var y;
				if (Tr) b: {
					switch (e) {
						case "compositionstart":
							var b = "onCompositionStart";
							break b;
						case "compositionend":
							b = "onCompositionEnd";
							break b;
						case "compositionupdate":
							b = "onCompositionUpdate";
							break b;
					}
					b = void 0;
				}
				else Nr ? jr(e, n) && (b = "onCompositionEnd") : e === "keydown" && n.keyCode === 229 && (b = "onCompositionStart");
				b && (Or && n.locale !== "ko" && (Nr || b !== "onCompositionStart" ? b === "onCompositionEnd" && Nr && (y = qn()) : (Wn = i, Gn = "value" in Wn ? Wn.value : Wn.textContent, Nr = !0)), v = up(r, b), 0 < v.length && (b = new dr(b, e, null, n, i), o.push({
					event: b,
					listeners: v
				}), y ? b.data = y : (y = Mr(n), y !== null && (b.data = y)))), (y = Dr ? Pr(e, n) : Fr(e, n)) && (b = up(r, "onBeforeInput"), 0 < b.length && (v = new dr("onBeforeInput", "beforeinput", null, n, i), o.push({
					event: v,
					listeners: b
				}), v.data = y)), Qf(o, e, r, n, i);
			}
			rp(o, t);
		});
	}
	function lp(e, t, n) {
		return {
			instance: e,
			listener: t,
			currentTarget: n
		};
	}
	function up(e, t) {
		for (var n = t + "Capture", r = []; e !== null;) {
			var i = e, a = i.stateNode;
			if (i = i.tag, i !== 5 && i !== 26 && i !== 27 || a === null || (i = Bn(e, n), i != null && r.unshift(lp(e, i, a)), i = Bn(e, t), i != null && r.push(lp(e, i, a))), e.tag === 3) return r;
			e = e.return;
		}
		return [];
	}
	function dp(e) {
		if (e === null) return null;
		do
			e = e.return;
		while (e && e.tag !== 5 && e.tag !== 27);
		return e || null;
	}
	function fp(e, t, n, r, i) {
		for (var a = t._reactName, o = []; n !== null && n !== r;) {
			var s = n, c = s.alternate, l = s.stateNode;
			if (s = s.tag, c !== null && c === r) break;
			s !== 5 && s !== 26 && s !== 27 || l === null || (c = l, i ? (l = Bn(n, a), l != null && o.unshift(lp(n, l, c))) : i || (l = Bn(n, a), l != null && o.push(lp(n, l, c)))), n = n.return;
		}
		o.length !== 0 && e.push({
			event: t,
			listeners: o
		});
	}
	var pp = /\r\n?/g, mp = /\u0000|\uFFFD/g;
	function hp(e) {
		return (typeof e == "string" ? e : "" + e).replace(pp, "\n").replace(mp, "");
	}
	function gp(e, t) {
		return t = hp(t), hp(e) === t;
	}
	function _p(e, t, n, r, i, o) {
		switch (n) {
			case "children":
				if (typeof r == "string") t === "body" || t === "textarea" && r === "" || wn(e, r);
				else if (typeof r == "number" || typeof r == "bigint") t !== "body" && wn(e, "" + r);
				else return;
				break;
			case "className":
				ln(e, "class", r);
				break;
			case "tabIndex":
				ln(e, "tabindex", r);
				break;
			case "dir":
			case "role":
			case "viewBox":
			case "width":
			case "height":
				ln(e, n, r);
				break;
			case "style":
				Dn(e, r, o);
				return;
			case "data": if (t !== "object") {
				ln(e, "data", r);
				break;
			}
			case "src":
			case "href":
				if (r === "" && (t !== "a" || n !== "href") || r == null || typeof r == "function" || typeof r == "symbol" || typeof r == "boolean") {
					e.removeAttribute(n);
					break;
				}
				r = jn(r), e.setAttribute(n, r);
				break;
			case "action":
			case "formAction":
				if (typeof r == "function") {
					e.setAttribute(n, "javascript:throw new Error('A React form was unexpectedly submitted. If you called form.submit() manually, consider using form.requestSubmit() instead. If you\\'re trying to use event.stopPropagation() in a submit event handler, consider also calling event.preventDefault().')");
					break;
				}
				if (typeof o == "function" && (n === "formAction" ? (t !== "input" && _p(e, t, "name", i.name, i, null), _p(e, t, "formEncType", i.formEncType, i, null), _p(e, t, "formMethod", i.formMethod, i, null), _p(e, t, "formTarget", i.formTarget, i, null)) : (_p(e, t, "encType", i.encType, i, null), _p(e, t, "method", i.method, i, null), _p(e, t, "target", i.target, i, null))), r == null || typeof r == "symbol" || typeof r == "boolean") {
					e.removeAttribute(n);
					break;
				}
				r = jn(r), e.setAttribute(n, r);
				break;
			case "onClick":
				r != null && (e.onclick = Mn);
				return;
			case "onScroll":
				r != null && I("scroll", e);
				return;
			case "onScrollEnd":
				r != null && I("scrollend", e);
				return;
			case "dangerouslySetInnerHTML":
				if (r != null) {
					if (typeof r != "object" || !("__html" in r)) throw Error(a(61));
					if (n = r.__html, n != null) {
						if (i.children != null) throw Error(a(60));
						o?.__html !== n && (e.innerHTML = n);
					}
				}
				break;
			case "multiple":
				e.multiple = r && typeof r != "function" && typeof r != "symbol";
				break;
			case "muted":
				e.muted = r && typeof r != "function" && typeof r != "symbol";
				break;
			case "suppressContentEditableWarning":
			case "suppressHydrationWarning":
			case "defaultValue":
			case "defaultChecked":
			case "innerHTML":
			case "ref": break;
			case "autoFocus": break;
			case "xlinkHref":
				if (r == null || typeof r == "function" || typeof r == "boolean" || typeof r == "symbol") {
					e.removeAttribute("xlink:href");
					break;
				}
				n = jn(r), e.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", n);
				break;
			case "contentEditable":
			case "spellCheck":
			case "draggable":
			case "value":
			case "autoReverse":
			case "externalResourcesRequired":
			case "focusable":
			case "preserveAlpha":
				r != null && typeof r != "function" && typeof r != "symbol" ? e.setAttribute(n, r) : e.removeAttribute(n);
				break;
			case "inert":
			case "allowFullScreen":
			case "async":
			case "autoPlay":
			case "controls":
			case "credentialless":
			case "default":
			case "defer":
			case "disabled":
			case "disablePictureInPicture":
			case "disableRemotePlayback":
			case "formNoValidate":
			case "hidden":
			case "loop":
			case "noModule":
			case "noValidate":
			case "open":
			case "playsInline":
			case "readOnly":
			case "required":
			case "reversed":
			case "scoped":
			case "seamless":
			case "itemScope":
				r && typeof r != "function" && typeof r != "symbol" ? e.setAttribute(n, "") : e.removeAttribute(n);
				break;
			case "capture":
			case "download":
				!0 === r ? e.setAttribute(n, "") : !1 !== r && r != null && typeof r != "function" && typeof r != "symbol" ? e.setAttribute(n, r) : e.removeAttribute(n);
				break;
			case "cols":
			case "rows":
			case "size":
			case "span":
				r != null && typeof r != "function" && typeof r != "symbol" && !isNaN(r) && 1 <= r ? e.setAttribute(n, r) : e.removeAttribute(n);
				break;
			case "rowSpan":
			case "start":
				r == null || typeof r == "function" || typeof r == "symbol" || isNaN(r) ? e.removeAttribute(n) : e.setAttribute(n, r);
				break;
			case "popover":
				I("beforetoggle", e), I("toggle", e), cn(e, "popover", r);
				break;
			case "xlinkActuate":
				un(e, "http://www.w3.org/1999/xlink", "xlink:actuate", r);
				break;
			case "xlinkArcrole":
				un(e, "http://www.w3.org/1999/xlink", "xlink:arcrole", r);
				break;
			case "xlinkRole":
				un(e, "http://www.w3.org/1999/xlink", "xlink:role", r);
				break;
			case "xlinkShow":
				un(e, "http://www.w3.org/1999/xlink", "xlink:show", r);
				break;
			case "xlinkTitle":
				un(e, "http://www.w3.org/1999/xlink", "xlink:title", r);
				break;
			case "xlinkType":
				un(e, "http://www.w3.org/1999/xlink", "xlink:type", r);
				break;
			case "xmlBase":
				un(e, "http://www.w3.org/XML/1998/namespace", "xml:base", r);
				break;
			case "xmlLang":
				un(e, "http://www.w3.org/XML/1998/namespace", "xml:lang", r);
				break;
			case "xmlSpace":
				un(e, "http://www.w3.org/XML/1998/namespace", "xml:space", r);
				break;
			case "is":
				cn(e, "is", r);
				break;
			case "innerText":
			case "textContent": return;
			default: if (!(2 < n.length) || n[0] !== "o" && n[0] !== "O" || n[1] !== "n" && n[1] !== "N") n = kn.get(n) || n, cn(e, n, r);
			else return;
		}
		T = !0;
	}
	function vp(e, t, n, r, i, o) {
		switch (n) {
			case "style":
				Dn(e, r, o);
				return;
			case "dangerouslySetInnerHTML":
				if (r != null) {
					if (typeof r != "object" || !("__html" in r)) throw Error(a(61));
					if (n = r.__html, n != null) {
						if (i.children != null) throw Error(a(60));
						o?.__html !== n && (e.innerHTML = n);
					}
				}
				break;
			case "children":
				if (typeof r == "string") wn(e, r);
				else if (typeof r == "number" || typeof r == "bigint") wn(e, "" + r);
				else return;
				break;
			case "onScroll":
				r != null && I("scroll", e);
				return;
			case "onScrollEnd":
				r != null && I("scrollend", e);
				return;
			case "onClick":
				r != null && (e.onclick = Mn);
				return;
			case "suppressContentEditableWarning":
			case "suppressHydrationWarning":
			case "innerHTML":
			case "ref": return;
			case "innerText":
			case "textContent": return;
			default:
				if (!$t.hasOwnProperty(n)) a: {
					if (n[0] === "o" && n[1] === "n" && (i = n.endsWith("Capture"), o = n.slice(2, i ? n.length - 7 : void 0), t = e[Lt] || null, t = t == null ? null : t[n], typeof t == "function" && e.removeEventListener(o, t, i), typeof r == "function")) {
						typeof t != "function" && t !== null && (n in e ? e[n] = null : e.hasAttribute(n) && e.removeAttribute(n)), e.addEventListener(o, r, i);
						break a;
					}
					T = !0, n in e ? e[n] = r : !0 === r ? e.setAttribute(n, "") : cn(e, n, r);
				}
				return;
		}
		T = !0;
	}
	function yp(e, t, n) {
		switch (t) {
			case "div":
			case "span":
			case "svg":
			case "path":
			case "a":
			case "g":
			case "p":
			case "li": break;
			case "img":
				I("error", e), I("load", e);
				var r = !1, i = !1, o;
				for (o in n) if (n.hasOwnProperty(o)) {
					var s = n[o];
					if (s != null) switch (o) {
						case "src":
							r = !0;
							break;
						case "srcSet":
							i = !0;
							break;
						case "children":
						case "dangerouslySetInnerHTML": throw Error(a(137, t));
						default: _p(e, t, o, s, n, null);
					}
				}
				i && _p(e, t, "srcSet", n.srcSet, n, null), r && _p(e, t, "src", n.src, n, null);
				return;
			case "input":
				I("invalid", e);
				var c = o = s = i = null, l = null, u = null;
				for (r in n) if (n.hasOwnProperty(r)) {
					var d = n[r];
					if (d != null) switch (r) {
						case "name":
							i = d;
							break;
						case "type":
							s = d;
							break;
						case "checked":
							l = d;
							break;
						case "defaultChecked":
							u = d;
							break;
						case "value":
							o = d;
							break;
						case "defaultValue":
							c = d;
							break;
						case "children":
						case "dangerouslySetInnerHTML":
							if (d != null) throw Error(a(137, t));
							break;
						default: _p(e, t, r, d, n, null);
					}
				}
				yn(e, o, c, l, u, s, i, !1);
				return;
			case "select":
				for (i in I("invalid", e), r = s = o = null, n) if (n.hasOwnProperty(i) && (c = n[i], c != null)) switch (i) {
					case "value":
						o = c;
						break;
					case "defaultValue":
						s = c;
						break;
					case "multiple": r = c;
					default: _p(e, t, i, c, n, null);
				}
				t = o, n = s, e.multiple = !!r, t == null ? n != null && xn(e, !!r, n, !0) : xn(e, !!r, t, !1);
				return;
			case "textarea":
				for (s in I("invalid", e), o = i = r = null, n) if (n.hasOwnProperty(s) && (c = n[s], c != null)) switch (s) {
					case "value":
						r = c;
						break;
					case "defaultValue":
						i = c;
						break;
					case "children":
						o = c;
						break;
					case "dangerouslySetInnerHTML":
						if (c != null) throw Error(a(91));
						break;
					default: _p(e, t, s, c, n, null);
				}
				Cn(e, r, i, o);
				return;
			case "option":
				for (l in n) if (n.hasOwnProperty(l) && (r = n[l], r != null)) switch (l) {
					case "selected":
						e.selected = r && typeof r != "function" && typeof r != "symbol";
						break;
					default: _p(e, t, l, r, n, null);
				}
				return;
			case "dialog":
				I("beforetoggle", e), I("toggle", e), I("cancel", e), I("close", e);
				break;
			case "iframe":
			case "object":
				I("load", e);
				break;
			case "video":
			case "audio":
				for (r = 0; r < tp.length; r++) I(tp[r], e);
				break;
			case "image":
				I("error", e), I("load", e);
				break;
			case "details":
				I("toggle", e);
				break;
			case "embed":
			case "source":
			case "link": I("error", e), I("load", e);
			case "area":
			case "base":
			case "br":
			case "col":
			case "hr":
			case "keygen":
			case "meta":
			case "param":
			case "track":
			case "wbr":
			case "menuitem":
				for (u in n) if (n.hasOwnProperty(u) && (r = n[u], r != null)) switch (u) {
					case "children":
					case "dangerouslySetInnerHTML": throw Error(a(137, t));
					default: _p(e, t, u, r, n, null);
				}
				return;
			default: if (On(t)) {
				for (d in n) n.hasOwnProperty(d) && (r = n[d], r !== void 0 && vp(e, t, d, r, n, void 0));
				return;
			}
		}
		for (c in n) n.hasOwnProperty(c) && (r = n[c], r != null && _p(e, t, c, r, n, null));
	}
	var bp = {};
	function xp(e, t, n, r) {
		switch (t) {
			case "div":
			case "span":
			case "svg":
			case "path":
			case "a":
			case "g":
			case "p":
			case "li": break;
			case "input":
				var i = null, o = null, s = null, c = null, l = null, u = null, d = null;
				for (m in n) {
					var f = n[m];
					if (n.hasOwnProperty(m) && f != null) switch (m) {
						case "checked": break;
						case "value": break;
						case "defaultValue": l = f;
						default: r.hasOwnProperty(m) || _p(e, t, m, null, r, f);
					}
				}
				for (var p in r) {
					var m = r[p];
					if (f = n[p], r.hasOwnProperty(p) && (m != null || f != null)) switch (p) {
						case "type":
							m !== f && (T = !0), o = m;
							break;
						case "name":
							m !== f && (T = !0), i = m;
							break;
						case "checked":
							m !== f && (T = !0), u = m;
							break;
						case "defaultChecked":
							m !== f && (T = !0), d = m;
							break;
						case "value":
							m !== f && (T = !0), s = m;
							break;
						case "defaultValue":
							m !== f && (T = !0), c = m;
							break;
						case "children":
						case "dangerouslySetInnerHTML":
							if (m != null) throw Error(a(137, t));
							break;
						default: m !== f && _p(e, t, p, m, r, f);
					}
				}
				vn(e, s, c, l, u, d, o, i);
				return;
			case "select":
				for (o in m = s = c = p = null, n) if (l = n[o], n.hasOwnProperty(o) && l != null) switch (o) {
					case "value": break;
					case "multiple": m = l;
					default: r.hasOwnProperty(o) || _p(e, t, o, null, r, l);
				}
				for (i in r) if (o = r[i], l = n[i], r.hasOwnProperty(i) && (o != null || l != null)) switch (i) {
					case "value":
						o !== l && (T = !0), p = o;
						break;
					case "defaultValue":
						o !== l && (T = !0), c = o;
						break;
					case "multiple": o !== l && (T = !0), s = o;
					default: o !== l && _p(e, t, i, o, r, l);
				}
				t = c, n = s, r = m, p == null ? !!r != !!n && (t == null ? xn(e, !!n, n ? [] : "", !1) : xn(e, !!n, t, !0)) : xn(e, !!n, p, !1);
				return;
			case "textarea":
				for (c in m = p = null, n) if (i = n[c], n.hasOwnProperty(c) && i != null && !r.hasOwnProperty(c)) switch (c) {
					case "value": break;
					case "children": break;
					default: _p(e, t, c, null, r, i);
				}
				for (s in r) if (i = r[s], o = n[s], r.hasOwnProperty(s) && (i != null || o != null)) switch (s) {
					case "value":
						i !== o && (T = !0), p = i;
						break;
					case "defaultValue":
						i !== o && (T = !0), m = i;
						break;
					case "children": break;
					case "dangerouslySetInnerHTML":
						if (i != null) throw Error(a(91));
						break;
					default: i !== o && _p(e, t, s, i, r, o);
				}
				Sn(e, p, m);
				return;
			case "option":
				for (var h in n) if (p = n[h], n.hasOwnProperty(h) && p != null && !r.hasOwnProperty(h)) switch (h) {
					case "selected":
						e.selected = !1;
						break;
					default: _p(e, t, h, null, r, p);
				}
				for (l in r) if (p = r[l], m = n[l], r.hasOwnProperty(l) && p !== m && (p != null || m != null)) switch (l) {
					case "selected":
						p !== m && (T = !0), e.selected = p && typeof p != "function" && typeof p != "symbol";
						break;
					default: _p(e, t, l, p, r, m);
				}
				return;
			case "img":
			case "link":
			case "area":
			case "base":
			case "br":
			case "col":
			case "embed":
			case "hr":
			case "keygen":
			case "meta":
			case "param":
			case "source":
			case "track":
			case "wbr":
			case "menuitem":
				for (var g in n) p = n[g], n.hasOwnProperty(g) && p != null && !r.hasOwnProperty(g) && _p(e, t, g, null, r, p);
				for (u in r) if (p = r[u], m = n[u], r.hasOwnProperty(u) && p !== m && (p != null || m != null)) switch (u) {
					case "children":
					case "dangerouslySetInnerHTML":
						if (p != null) throw Error(a(137, t));
						break;
					default: _p(e, t, u, p, r, m);
				}
				return;
			default: if (On(t)) {
				for (var _ in n) p = n[_], n.hasOwnProperty(_) && p !== void 0 && !r.hasOwnProperty(_) && vp(e, t, _, void 0, r, p);
				for (d in r) p = r[d], m = n[d], !r.hasOwnProperty(d) || p === m || p === void 0 && m === void 0 || vp(e, t, d, p, r, m);
				return;
			}
		}
		for (var v in n) p = n[v], n.hasOwnProperty(v) && p != null && !r.hasOwnProperty(v) && _p(e, t, v, null, r, p);
		for (f in r) p = r[f], m = n[f], !r.hasOwnProperty(f) || p === m || p == null && m == null || _p(e, t, f, p, r, m);
	}
	function Sp(e) {
		switch (e) {
			case "css":
			case "script":
			case "font":
			case "img":
			case "image":
			case "input":
			case "link": return !0;
			default: return !1;
		}
	}
	function Cp() {
		if (typeof performance.getEntriesByType == "function") {
			for (var e = 0, t = 0, n = performance.getEntriesByType("resource"), r = 0; r < n.length; r++) {
				var i = n[r], a = i.transferSize, o = i.initiatorType, s = i.duration;
				if (a && s && Sp(o)) {
					for (o = 0, s = i.responseEnd, r += 1; r < n.length; r++) {
						var c = n[r], l = c.startTime;
						if (l > s) break;
						var u = c.transferSize, d = c.initiatorType;
						u && Sp(d) && (c = c.responseEnd, o += u * (c < s ? 1 : (s - l) / (c - l)));
					}
					if (--r, t += 8 * (a + o) / (i.duration / 1e3), e++, 10 < e) break;
				}
			}
			if (0 < e) return t / e / 1e6;
		}
		return navigator.connection && (e = navigator.connection.downlink, typeof e == "number") ? e : 5;
	}
	var wp = null, Tp = null;
	function Ep(e) {
		return e.nodeType === 9 ? e : e.ownerDocument;
	}
	function Dp(e) {
		switch (e) {
			case "http://www.w3.org/2000/svg": return 1;
			case "http://www.w3.org/1998/Math/MathML": return 2;
			default: return 0;
		}
	}
	function Op(e, t) {
		if (e === 0) switch (t) {
			case "svg": return 1;
			case "math": return 2;
			default: return 0;
		}
		return e === 1 && t === "foreignObject" ? 0 : e;
	}
	function kp(e, t, n, r) {
		return n = Ep(n).createElement(e), n[It] = r, n[Lt] = t, yp(n, e, t), Xt(n), n;
	}
	function Ap(e, t) {
		return e === "textarea" || e === "noscript" || typeof t.children == "string" || typeof t.children == "number" || typeof t.children == "bigint" || typeof t.dangerouslySetInnerHTML == "object" && t.dangerouslySetInnerHTML !== null && t.dangerouslySetInnerHTML.__html != null;
	}
	var jp = null;
	function Mp() {
		var e = window.event;
		return e && e.type === "popstate" ? e !== jp && (jp = e, !0) : (jp = null, !1);
	}
	var Np = typeof setTimeout == "function" ? setTimeout : void 0, Pp = typeof clearTimeout == "function" ? clearTimeout : void 0, Fp = typeof Promise == "function" ? Promise : void 0, Ip = typeof requestAnimationFrame == "function" ? requestAnimationFrame : Np, Lp = typeof queueMicrotask == "function" ? queueMicrotask : Fp === void 0 ? Np : function(e) {
		return Fp.resolve(null).then(e).catch(Rp);
	};
	function Rp(e) {
		setTimeout(function() {
			throw e;
		});
	}
	function zp(e) {
		return e === "head";
	}
	function Bp(e, t) {
		var n = t, r = 0;
		do {
			var i = n.nextSibling;
			if (e.removeChild(n), i && i.nodeType === 8) {
				if (n = i.data, n === "/$" || n === "/&") {
					if (r === 0) {
						e.removeChild(i), ng(t);
						return;
					}
					r--;
				} else if (n === "$" || n === "$?" || n === "$~" || n === "$!" || n === "&") r++;
				else if (n === "html") Nm(e.ownerDocument.documentElement);
				else if (n === "head") {
					n = e.ownerDocument.head, Nm(n);
					for (var a = n.firstChild; a;) {
						var o = a.nextSibling, s = a.nodeName;
						a[Ut] || s === "SCRIPT" || s === "STYLE" || s === "LINK" && a.rel.toLowerCase() === "stylesheet" || n.removeChild(a), a = o;
					}
				} else n === "body" && Nm(e.ownerDocument.body);
			}
			n = i;
		} while (n);
		ng(t);
	}
	function Vp(e, t) {
		var n = e;
		e = 0;
		do {
			var r = n.nextSibling;
			if (n.nodeType === 1 ? t ? (n._stashedDisplay = n.style.display, n.style.display = "none") : (n.style.display = n._stashedDisplay || "", n.getAttribute("style") === "" && n.removeAttribute("style")) : n.nodeType === 3 && (t ? (n._stashedText = n.nodeValue, n.nodeValue = "") : n.nodeValue = n._stashedText || ""), r && r.nodeType === 8) {
				if (n = r.data, n === "/$") {
					if (e === 0) break;
					e--;
				} else n !== "$" && n !== "$?" && n !== "$~" && n !== "$!" || e++;
			}
			n = r;
		} while (n);
	}
	function Hp(e, t, n) {
		if (t = CSS.escape(t) === t ? t : "r-" + btoa(t).replace(/=/g, ""), e.style.viewTransitionName = t, n != null && (e.style.viewTransitionClass = n), n = getComputedStyle(e), n.display === "inline") {
			if (t = e.getClientRects(), t.length === 1) var r = 1;
			else for (var i = r = 0; i < t.length; i++) {
				var a = t[i];
				0 < a.width && 0 < a.height && r++;
			}
			r === 1 && (e = e.style, e.display = t.length === 1 ? "inline-block" : "block", e.marginTop = "-" + n.paddingTop, e.marginBottom = "-" + n.paddingBottom);
		}
	}
	function Up(e, t) {
		e = e.style, t = t.style;
		var n = t == null ? null : t.hasOwnProperty("viewTransitionName") ? t.viewTransitionName : t.hasOwnProperty("view-transition-name") ? t["view-transition-name"] : null;
		e.viewTransitionName = n == null || typeof n == "boolean" ? "" : ("" + n).trim(), n = t == null ? null : t.hasOwnProperty("viewTransitionClass") ? t.viewTransitionClass : t.hasOwnProperty("view-transition-class") ? t["view-transition-class"] : null, e.viewTransitionClass = n == null || typeof n == "boolean" ? "" : ("" + n).trim(), e.display === "inline-block" && (t == null ? e.display = e.margin = "" : (n = t.display, e.display = n == null || typeof n == "boolean" ? "" : n, n = t.margin, n == null ? (n = t.hasOwnProperty("marginTop") ? t.marginTop : t["margin-top"], e.marginTop = n == null || typeof n == "boolean" ? "" : n, t = t.hasOwnProperty("marginBottom") ? t.marginBottom : t["margin-bottom"], e.marginBottom = t == null || typeof t == "boolean" ? "" : t) : e.margin = n));
	}
	function Wp(e, t, n) {
		return n = n.ownerDocument.defaultView, {
			rect: e,
			abs: t.position === "absolute" || t.position === "fixed",
			clip: t.clipPath !== "none" || t.overflow !== "visible" || t.filter !== "none" || t.mask !== "none" || t.mask !== "none" || t.borderRadius !== "0px",
			view: 0 <= e.bottom && 0 <= e.right && e.top <= n.innerHeight && e.left <= n.innerWidth
		};
	}
	function Gp(e) {
		return Wp(e.getBoundingClientRect(), getComputedStyle(e), e);
	}
	function Kp(e) {
		var t = e.getBoundingClientRect();
		t = new DOMRect(t.x + 2e4, t.y + 2e4, t.width, t.height);
		var n = getComputedStyle(e);
		return Wp(t, n, e);
	}
	function qp(e) {
		return e.documentElement.clientHeight;
	}
	function Jp(e) {
		this.addEventListener("load", e), this.addEventListener("error", e);
	}
	function Yp(e, t, n, r, i, a, o, s, c) {
		var l = t.nodeType === 9 ? t : t.ownerDocument;
		try {
			var u = l.startViewTransition({
				update: function() {
					var t = l.defaultView, n = t.navigation && t.navigation.transition, o = l.fonts.status;
					r();
					var s = [];
					if (o === "loaded" && (qp(l), l.fonts.status === "loading" && s.push(l.fonts.ready)), o = s.length, e !== null) for (var c = e.suspenseyImages, u = 0, d = 0; d < c.length; d++) {
						var f = c[d];
						if (!f.complete) {
							var p = f.getBoundingClientRect();
							if (0 < p.bottom && 0 < p.right && p.top < t.innerHeight && p.left < t.innerWidth) {
								if (u += uh(f), u > ph) {
									s.length = o;
									break;
								}
								f = new Promise(Jp.bind(f)), s.push(f);
							}
						}
					}
					if (0 < s.length) return t = Promise.race([Promise.all(s), new Promise(function(e) {
						return setTimeout(e, 500);
					})]).then(i, i), (n ? Promise.allSettled([n.finished, t]) : t).then(a, a);
					if (i(), n) return n.finished.then(a, a);
					a();
				},
				types: n
			});
			l.__reactViewTransition = u;
			var d = [];
			return u.ready.then(function() {
				for (var e = l.documentElement.getAnimations({ subtree: !0 }), t = 0; t < e.length; t++) {
					var n = e[t], r = n.effect, i = r.pseudoElement;
					if (i != null && i.startsWith("::view-transition")) {
						d.push(n), n = r.getKeyframes();
						for (var a = i = void 0, s = !0, c = 0; c < n.length; c++) {
							var u = n[c], f = u.width;
							if (i === void 0) i = f;
							else if (i !== f) {
								s = !1;
								break;
							}
							if (f = u.height, a === void 0) a = f;
							else if (a !== f) {
								s = !1;
								break;
							}
							delete u.width, delete u.height, u.transform === "none" && delete u.transform;
						}
						s && i !== void 0 && a !== void 0 && (r.setKeyframes(n), s = getComputedStyle(r.target, r.pseudoElement), s.width !== i || s.height !== a) && (s = n[0], s.width = i, s.height = a, s = n[n.length - 1], s.width = i, s.height = a, r.setKeyframes(n));
					}
				}
				o();
			}, function(e) {
				l.__reactViewTransition === u && (l.__reactViewTransition = null);
				try {
					if (typeof e == "object" && e) switch (e.name) {
						case "InvalidStateError": (e.message === "View transition was skipped because document visibility state is hidden." || e.message === "Skipping view transition because document visibility state has become hidden." || e.message === "Skipping view transition because viewport size changed." || e.message === "Transition was aborted because of invalid state") && (e = null);
					}
					e !== null && c(e);
				} finally {
					r(), i(), o();
				}
			}), u.finished.finally(function() {
				for (var e = 0; e < d.length; e++) d[e].cancel();
				l.__reactViewTransition === u && (l.__reactViewTransition = null), s();
			}), u;
		} catch {
			return r(), i(), o(), null;
		}
	}
	function Xp(e, t) {
		this._scope = document.documentElement, this._selector = "::view-transition-" + e + "(" + t + ")";
	}
	Xp.prototype.animate = function(e, t) {
		return t = typeof t == "number" ? { duration: t } : x({}, t), t.pseudoElement = this._selector, this._scope.animate(e, t);
	}, Xp.prototype.getAnimations = function() {
		for (var e = this._scope, t = this._selector, n = e.getAnimations({ subtree: !0 }), r = [], i = 0; i < n.length; i++) {
			var a = n[i].effect;
			a !== null && a.target === e && a.pseudoElement === t && r.push(n[i]);
		}
		return r;
	}, Xp.prototype.getComputedStyle = function() {
		return getComputedStyle(this._scope, this._selector);
	};
	function Zp(e) {
		return {
			name: e,
			group: new Xp("group", e),
			imagePair: new Xp("image-pair", e),
			old: new Xp("old", e),
			new: new Xp("new", e)
		};
	}
	function Qp(e) {
		this._fragmentFiber = e, this._observers = this._eventListeners = null;
	}
	Qp.prototype.addEventListener = function(e, t, n) {
		var r = null, i = null;
		if (!(n != null && typeof n != "boolean" && (r = n.signal || null, r !== null && r.aborted))) {
			this._eventListeners === null && (this._eventListeners = []);
			var a = this._eventListeners;
			if (rm(a, e, t, n) === -1) {
				var o = this, s = t;
				n != null && typeof n != "boolean" && !0 === n.once && (s = function(r) {
					o.removeEventListener(e, t, n), typeof t == "function" ? t.call(this, r) : t.handleEvent(r);
				}), r !== null && (i = o.removeEventListener.bind(o, e, t, n), r.addEventListener("abort", i, { once: !0 }), i = r.removeEventListener.bind(r, "abort", i)), r = tm(n), a.push({
					type: e,
					listener: t,
					optionsOrUseCapture: n,
					attachedListener: s,
					cleanup: i
				}), h(this._fragmentFiber.child, !1, $p, e, s, r);
			}
			this._eventListeners = a;
		}
	};
	function $p(e, t, n, r) {
		return b(e).addEventListener(t, n, r), !1;
	}
	Qp.prototype.removeEventListener = function(e, t, n) {
		var r = this._eventListeners;
		if (r !== null && (t = rm(r, e, t, n), t !== -1)) {
			var i = r[t];
			n = i.attachedListener;
			var a = i.cleanup;
			i = tm(i.optionsOrUseCapture), h(this._fragmentFiber.child, !1, em, e, n, i), r.splice(t, 1), a !== null && a();
		}
	};
	function em(e, t, n, r) {
		return b(e).removeEventListener(t, n, r), !1;
	}
	function tm(e) {
		return e != null && typeof e != "boolean" && (!0 === e.once || e.signal instanceof AbortSignal) ? {
			capture: e.capture,
			passive: e.passive
		} : e;
	}
	function nm(e) {
		return e == null ? "c=0" : typeof e == "boolean" ? "c=" + (e ? "1" : "0") : "c=" + (e.capture ? "1" : "0");
	}
	function rm(e, t, n, r) {
		if (e.length === 0) return -1;
		r = nm(r);
		for (var i = 0; i < e.length; i++) {
			var a = e[i];
			if (a.type === t && a.listener === n && nm(a.optionsOrUseCapture) === r) return i;
		}
		return -1;
	}
	Qp.prototype.dispatchEvent = function(e) {
		var t = g(this._fragmentFiber);
		if (t === null) return !0;
		t = b(t);
		var n = this._eventListeners;
		if (n !== null && 0 < n.length || !e.bubbles) {
			var r = t.nodeType === 9 ? t.createComment("") : document.createTextNode("");
			if (n) for (var i = 0; i < n.length; i++) {
				var a = n[i];
				r.addEventListener(a.type, a.attachedListener, tm(a.optionsOrUseCapture));
			}
			if (t.appendChild(r), e = r.dispatchEvent(e), n) for (i = 0; i < n.length; i++) a = n[i], r.removeEventListener(a.type, a.attachedListener, tm(a.optionsOrUseCapture));
			return t.removeChild(r), e;
		}
		return t.dispatchEvent(e);
	}, Qp.prototype.focus = function(e) {
		h(this._fragmentFiber.child, !0, im, e, void 0, void 0);
	};
	function im(e, t) {
		return e.tag !== 6 && (e = b(e), km(e, t));
	}
	Qp.prototype.focusLast = function(e) {
		var t = [];
		h(this._fragmentFiber.child, !0, am, t, void 0, void 0);
		for (var n = t.length - 1; 0 <= n && !im(t[n], e); n--);
	};
	function am(e, t) {
		return t.push(e), !1;
	}
	Qp.prototype.blur = function() {
		var e = g(this._fragmentFiber);
		e !== null && (e = b(e), e = Ep(e).activeElement, e !== null && h(this._fragmentFiber.child, !1, om, e, void 0, void 0));
	};
	function om(e, t) {
		return e.tag !== 6 && (e = b(e), e === t || e.contains(t) ? (t.blur(), !0) : !1);
	}
	Qp.prototype.observeUsing = function(e) {
		this._observers === null && (this._observers = /* @__PURE__ */ new Set()), this._observers.add(e), h(this._fragmentFiber.child, !1, sm, e, void 0, void 0);
	};
	function sm(e, t) {
		return e.tag === 6 || (e = b(e), t.observe(e)), !1;
	}
	Qp.prototype.unobserveUsing = function(e) {
		var t = this._observers;
		if (t !== null && t.has(e)) {
			t.delete(e), h(this._fragmentFiber.child, !1, cm, e, void 0, void 0);
			for (var n = t = 0; n < lm.length; n++) {
				var r = lm[n];
				r.fragmentInstance === this && r.observer === e ? e.unobserve(r.instance) : lm[t++] = r;
			}
			lm.length = t;
		}
	};
	function cm(e, t) {
		return e.tag === 6 || (e = b(e), t.unobserve(e)), !1;
	}
	var lm = [], um = !1;
	function dm(e, t, n) {
		lm.push({
			fragmentInstance: e,
			observer: t,
			instance: n
		}), um || (um = !0, Am(function() {
			um = !1;
			var e = lm;
			lm = [];
			for (var t = 0; t < e.length; t++) {
				var n = e[t];
				n.observer.unobserve(n.instance);
			}
		}));
	}
	Qp.prototype.getClientRects = function() {
		var e = [];
		return h(this._fragmentFiber.child, !1, fm, e, void 0, void 0), e;
	};
	function fm(e, t) {
		if (e.tag === 6) {
			e = e.stateNode;
			var n = e.ownerDocument.createRange();
			n.selectNodeContents(e), t.push.apply(t, n.getClientRects());
		} else e = b(e), t.push.apply(t, e.getClientRects());
		return !1;
	}
	Qp.prototype.getRootNode = function(e) {
		var t = g(this._fragmentFiber);
		return t === null ? this : b(t).getRootNode(e);
	}, Qp.prototype.compareDocumentPosition = function(e) {
		var t = g(this._fragmentFiber);
		if (t === null) return Node.DOCUMENT_POSITION_DISCONNECTED;
		var n = [];
		h(this._fragmentFiber.child, !1, am, n, void 0, void 0);
		var r = b(t);
		if (n.length === 0) {
			if (n = r, _(this._fragmentFiber)) {
				a: {
					for (t = this._fragmentFiber.return; t !== null;) {
						if (t.tag === 4) {
							t = t.stateNode.containerInfo;
							break a;
						}
						if (t.tag === 3 || t.tag === 5 || t.tag === 27) break;
						t = t.return;
					}
					t = null;
				}
				t != null && (n = t);
			}
			t = this._fragmentFiber;
			var i = r = n.compareDocumentPosition(e);
			return n === e ? i = Node.DOCUMENT_POSITION_CONTAINS : r & Node.DOCUMENT_POSITION_CONTAINED_BY && (n = v(t)[1], n === null ? i = Node.DOCUMENT_POSITION_PRECEDING : (e = b(n).compareDocumentPosition(e), i = e === 0 || e & Node.DOCUMENT_POSITION_FOLLOWING ? Node.DOCUMENT_POSITION_FOLLOWING : Node.DOCUMENT_POSITION_PRECEDING)), i |= Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC;
		}
		t = b(n[0]), i = b(n[n.length - 1]);
		var a = _(this._fragmentFiber) ? t.parentElement : r;
		if (a == null) return Node.DOCUMENT_POSITION_DISCONNECTED;
		r = a.compareDocumentPosition(t) & Node.DOCUMENT_POSITION_CONTAINED_BY, a = a.compareDocumentPosition(i) & Node.DOCUMENT_POSITION_CONTAINED_BY;
		var o = t.compareDocumentPosition(e), s = i.compareDocumentPosition(e), c = o & Node.DOCUMENT_POSITION_CONTAINED_BY || s & Node.DOCUMENT_POSITION_CONTAINED_BY;
		return s = r && a && o & Node.DOCUMENT_POSITION_FOLLOWING && s & Node.DOCUMENT_POSITION_PRECEDING, t = r && t === e || a && i === e || c || s ? Node.DOCUMENT_POSITION_CONTAINED_BY : !r && t === e || !a && i === e ? Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC : o, t & Node.DOCUMENT_POSITION_DISCONNECTED || t & Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC || pm(t, this._fragmentFiber, n[0], n[n.length - 1], e) ? t : Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC;
	};
	function pm(e, t, n, r, i) {
		var a = Kt(i);
		if (e & Node.DOCUMENT_POSITION_CONTAINED_BY) {
			if (n = !!a) a: {
				for (; a !== null;) {
					if (a.tag === 7 && (a === t || a.alternate === t)) {
						n = !0;
						break a;
					}
					a = a.return;
				}
				n = !1;
			}
			return n;
		}
		if (e & Node.DOCUMENT_POSITION_CONTAINS) {
			if (a === null) return a = i.ownerDocument, i === a || i === a.documentElement || i === a.body;
			a: {
				for (a = t, t = g(t); a !== null;) {
					if (!(a.tag !== 5 && a.tag !== 3 && a.tag !== 27 || a !== t && a.alternate !== t)) {
						a = !0;
						break a;
					}
					a = a.return;
				}
				a = !1;
			}
			return a;
		}
		return e & Node.DOCUMENT_POSITION_PRECEDING ? ((t = !!a) && !(t = a === n) && (t = ae(n, a, ie), t === null ? t = !1 : (h(t, !0, ne, a, n), a = ee, ee = null, t = a !== null)), t) : e & Node.DOCUMENT_POSITION_FOLLOWING ? ((t = !!a) && !(t = a === r) && (t = ae(r, a, ie), t === null ? t = !1 : (h(t, !0, re, a, r), a = ee, te = ee = null, t = a !== null)), t) : !1;
	}
	function mm(e, t) {
		var n = e.ownerDocument.createRange();
		n.selectNodeContents(e), e = n.getBoundingClientRect(), window.scrollTo(window.scrollX + e.left, t ? window.scrollY + e.top : window.scrollY + e.bottom - window.innerHeight);
	}
	Qp.prototype.scrollIntoView = function(e) {
		if (typeof e == "object") throw Error(a(566));
		var t = [];
		h(this._fragmentFiber.child, !1, am, t, void 0, void 0);
		var n = !1 !== e;
		if (t.length === 0) {
			var r = v(this._fragmentFiber);
			if (r = n ? r[1] || r[0] || g(this._fragmentFiber) : r[0] || r[1], r === null) return;
			if (r.tag === 6) {
				e = b(r), mm(e, n);
				return;
			}
			if (r = b(r), r.nodeType !== 9) {
				if (r.nodeType === 11) {
					n = "host" in r ? r.host : null, n !== null && n.scrollIntoView(e);
					return;
				}
				r.scrollIntoView(e);
			}
		}
		for (r = n ? t.length - 1 : 0; r !== (n ? -1 : t.length);) {
			var i = t[r];
			i.tag === 6 ? (i = b(i), mm(i, n)) : b(i).scrollIntoView(e), r += n ? -1 : 1;
		}
	};
	function hm(e, t) {
		return e = b(e), gm(e, t), !1;
	}
	function gm(e, t) {
		e.reactFragments ??= /* @__PURE__ */ new Set(), e.reactFragments.add(t);
	}
	function _m(e, t) {
		var n = t._eventListeners;
		if (n !== null) for (var r = 0; r < n.length; r++) {
			var i = n[r];
			e.addEventListener(i.type, i.attachedListener, tm(i.optionsOrUseCapture));
		}
		e.nodeType !== 3 && (n = t._observers, n !== null && n.forEach(function(n) {
			for (var r = 0, i = 0; i < lm.length; i++) {
				var a = lm[i];
				(a.fragmentInstance !== t || a.observer !== n || a.instance !== e) && (lm[r++] = a);
			}
			lm.length = r, n.observe(e);
		}), gm(e, t));
	}
	function vm(e, t) {
		var n = t._eventListeners;
		if (n !== null) for (var r = 0; r < n.length; r++) {
			var i = n[r];
			e.removeEventListener(i.type, i.attachedListener, tm(i.optionsOrUseCapture));
		}
		e.nodeType !== 3 && (n = t._observers, n !== null && n.forEach(function(n) {
			typeof n.rootMargin == "string" ? dm(t, n, e) : n.unobserve(e);
		}), e.reactFragments != null && e.reactFragments.delete(t));
	}
	function ym(e) {
		var t = e.firstChild;
		for (t && t.nodeType === 10 && (t = t.nextSibling); t;) {
			var n = t;
			switch (t = t.nextSibling, n.nodeName) {
				case "HTML":
				case "HEAD":
				case "BODY":
					ym(n), Gt(n);
					continue;
				case "SCRIPT":
				case "STYLE": continue;
				case "LINK": if (n.rel.toLowerCase() === "stylesheet") continue;
			}
			e.removeChild(n);
		}
	}
	function bm(e, t, n, r) {
		for (; e.nodeType === 1;) {
			var i = n;
			if (e.nodeName.toLowerCase() !== t.toLowerCase()) {
				if (!r && (e.nodeName !== "INPUT" || e.type !== "hidden")) break;
			} else if (!r) {
				if (t === "input" && e.type === "hidden") {
					var a = i.name == null ? null : "" + i.name;
					if (i.type === "hidden" && e.getAttribute("name") === a) return e;
				} else return e;
			} else if (!e[Ut]) switch (t) {
				case "meta":
					if (!e.hasAttribute("itemprop")) break;
					return e;
				case "link":
					if (a = e.getAttribute("rel"), a === "stylesheet" && e.hasAttribute("data-precedence") || a !== i.rel || e.getAttribute("href") !== (i.href == null || i.href === "" ? null : i.href) || e.getAttribute("crossorigin") !== (i.crossOrigin == null ? null : i.crossOrigin) || e.getAttribute("title") !== (i.title == null ? null : i.title)) break;
					return e;
				case "style":
					if (e.hasAttribute("data-precedence")) break;
					return e;
				case "script":
					if (a = e.getAttribute("src"), (a !== (i.src == null ? null : i.src) || e.getAttribute("type") !== (i.type == null ? null : i.type) || e.getAttribute("crossorigin") !== (i.crossOrigin == null ? null : i.crossOrigin)) && a && e.hasAttribute("async") && !e.hasAttribute("itemprop")) break;
					return e;
				default: return e;
			}
			if (e = Em(e.nextSibling), e === null) break;
		}
		return null;
	}
	function xm(e, t, n) {
		if (t === "") return null;
		for (; e.nodeType !== 3;) if ((e.nodeType !== 1 || e.nodeName !== "INPUT" || e.type !== "hidden") && !n || (e = Em(e.nextSibling), e === null)) return null;
		return e;
	}
	function Sm(e, t) {
		for (; e.nodeType !== 8;) if ((e.nodeType !== 1 || e.nodeName !== "INPUT" || e.type !== "hidden") && !t || (e = Em(e.nextSibling), e === null)) return null;
		return e;
	}
	function Cm(e) {
		return e.data === "$?" || e.data === "$~";
	}
	function wm(e) {
		return e.data === "$!" || e.data === "$?" && e.ownerDocument.readyState !== "loading";
	}
	function Tm(e, t) {
		var n = e.ownerDocument;
		if (e.data === "$~") e._reactRetry = t;
		else if (e.data !== "$?" || n.readyState !== "loading") t();
		else {
			var r = function() {
				t(), n.removeEventListener("DOMContentLoaded", r);
			};
			n.addEventListener("DOMContentLoaded", r), e._reactRetry = r;
		}
	}
	function Em(e) {
		for (; e != null; e = e.nextSibling) {
			var t = e.nodeType;
			if (t === 1 || t === 3) break;
			if (t === 8) {
				if (t = e.data, t === "$" || t === "$!" || t === "$?" || t === "$~" || t === "&" || t === "F!" || t === "F") break;
				if (t === "/$" || t === "/&") return null;
			}
		}
		return e;
	}
	var Dm = null;
	function L(e) {
		e = e.nextSibling;
		for (var t = 0; e;) {
			if (e.nodeType === 8) {
				var n = e.data;
				if (n === "/$" || n === "/&") {
					if (t === 0) return Em(e.nextSibling);
					t--;
				} else n !== "$" && n !== "$!" && n !== "$?" && n !== "$~" && n !== "&" || t++;
			}
			e = e.nextSibling;
		}
		return null;
	}
	function Om(e) {
		e = e.previousSibling;
		for (var t = 0; e;) {
			if (e.nodeType === 8) {
				var n = e.data;
				if (n === "$" || n === "$!" || n === "$?" || n === "$~" || n === "&") {
					if (t === 0) return e;
					t--;
				} else n !== "/$" && n !== "/&" || t++;
			}
			e = e.previousSibling;
		}
		return null;
	}
	function km(e, t) {
		function n() {
			r = !0;
		}
		if (e.ownerDocument.activeElement === e) return !0;
		var r = !1;
		try {
			e.ownerDocument.addEventListener("focus", n, !0), (e.focus || HTMLElement.prototype.focus).call(e, t);
		} finally {
			e.ownerDocument.removeEventListener("focus", n, !0);
		}
		return r;
	}
	function Am(e) {
		Ip(function() {
			Ip(function(t) {
				return e(t);
			});
		});
	}
	function jm(e, t, n) {
		switch (t = Ep(n), e) {
			case "html":
				if (e = t.documentElement, !e) throw Error(a(452));
				return e;
			case "head":
				if (e = t.head, !e) throw Error(a(453));
				return e;
			case "body":
				if (e = t.body, !e) throw Error(a(454));
				return e;
			default: throw Error(a(451));
		}
	}
	function Mm(e, t, n) {
		for (var r in n) {
			var i = n[r];
			n.hasOwnProperty(r) && i != null && _p(e, t, r, null, bp, i);
		}
		n.dangerouslySetInnerHTML != null && (e.textContent = ""), e.onclick === Mn && (e.onclick = null), Gt(e);
	}
	function Nm(e) {
		for (var t = e.attributes; t.length;) e.removeAttributeNode(t[0]);
		Gt(e);
	}
	var Pm = /* @__PURE__ */ new Map(), Fm = /* @__PURE__ */ new Set();
	function Im(e) {
		if (typeof e.getRootNode == "function") {
			var t = e.getRootNode();
			if (t.nodeType === 9 || t.nodeType === 11) return t;
		}
		return e.nodeType === 9 ? e : e.ownerDocument;
	}
	var Lm = Oe.d;
	Oe.d = {
		f: Rm,
		r: zm,
		D: Hm,
		C: Um,
		L: Wm,
		m: Gm,
		X: qm,
		S: Km,
		M: Jm
	};
	function Rm() {
		var e = Lm.f(), t = ef();
		return e || t;
	}
	function zm(e) {
		var t = qt(e);
		t !== null && t.tag === 5 && t.type === "form" ? hc(t) : Lm.r(e);
	}
	var Bm = typeof document > "u" ? null : document;
	function Vm(e, t, n) {
		var r = Bm;
		if (r && typeof t == "string" && t) {
			var i = _n(t);
			i = "link[rel=\"" + e + "\"][href=\"" + i + "\"]", typeof n == "string" && (i += "[crossorigin=\"" + n + "\"]"), Fm.has(i) || (Fm.add(i), e = {
				rel: e,
				crossOrigin: n,
				href: t
			}, r.querySelector(i) === null && (t = r.createElement("link"), yp(t, "link", e), Xt(t), r.head.appendChild(t)));
		}
	}
	function Hm(e) {
		Lm.D(e), Vm("dns-prefetch", e, null);
	}
	function Um(e, t) {
		Lm.C(e, t), Vm("preconnect", e, t);
	}
	function Wm(e, t, n) {
		Lm.L(e, t, n);
		var r = Bm;
		if (r && e && t) {
			var i = "link[rel=\"preload\"][as=\"" + _n(t) + "\"]";
			t === "image" && n && n.imageSrcSet ? (i += "[imagesrcset=\"" + _n(n.imageSrcSet) + "\"]", typeof n.imageSizes == "string" && (i += "[imagesizes=\"" + _n(n.imageSizes) + "\"]")) : i += "[href=\"" + _n(e) + "\"]";
			var a = i;
			switch (t) {
				case "style":
					a = z(e);
					break;
				case "script": a = Qm(e);
			}
			if (!(Pm.has(a) || (e = x({
				rel: "preload",
				href: t === "image" && n && n.imageSrcSet ? void 0 : e,
				as: t
			}, n), Pm.set(a, e), r.querySelector(i) !== null || t === "style" && r.querySelector(Ym(a)) || t === "script" && r.querySelector($m(a))))) {
				var o = r.createElement("link");
				yp(o, "link", e), t === "style" && (o[Wt] = !0, o.onload = o.onerror = function() {
					Zt(o);
				}), Xt(o), r.head.appendChild(o);
			}
		}
	}
	function Gm(e, t) {
		Lm.m(e, t);
		var n = Bm;
		if (n && e) {
			var r = t && typeof t.as == "string" ? t.as : "script", i = "link[rel=\"modulepreload\"][as=\"" + _n(r) + "\"][href=\"" + _n(e) + "\"]", a = i;
			switch (r) {
				case "audioworklet":
				case "paintworklet":
				case "serviceworker":
				case "sharedworker":
				case "worker":
				case "script": a = Qm(e);
			}
			if (!Pm.has(a) && (e = x({
				rel: "modulepreload",
				href: e
			}, t), Pm.set(a, e), n.querySelector(i) === null)) {
				switch (r) {
					case "audioworklet":
					case "paintworklet":
					case "serviceworker":
					case "sharedworker":
					case "worker":
					case "script": if (n.querySelector($m(a))) return;
				}
				r = n.createElement("link"), yp(r, "link", e), Xt(r), n.head.appendChild(r);
			}
		}
	}
	function Km(e, t, n) {
		Lm.S(e, t, n);
		var r = Bm;
		if (r && e) {
			var i = Yt(r).hoistableStyles, a = z(e);
			t ||= "default";
			var o = i.get(a);
			if (!o) {
				var s = {
					loading: 0,
					preload: null
				};
				if (o = r.querySelector(Ym(a))) s.loading = 5;
				else {
					e = x({
						rel: "stylesheet",
						href: e,
						"data-precedence": t
					}, n), (n = Pm.get(a)) && nh(e, n);
					var c = o = r.createElement("link");
					Xt(c), yp(c, "link", e), c._p = new Promise(function(e, t) {
						c.onload = e, c.onerror = t;
					}), c.addEventListener("load", function() {
						s.loading |= 1;
					}), c.addEventListener("error", function() {
						s.loading |= 2;
					}), s.loading |= 4, th(o, t, r);
				}
				o = {
					type: "stylesheet",
					instance: o,
					count: 1,
					state: s
				}, i.set(a, o);
			}
		}
	}
	function qm(e, t) {
		Lm.X(e, t);
		var n = Bm;
		if (n && e) {
			var r = Yt(n).hoistableScripts, i = Qm(e), a = r.get(i);
			a || (a = n.querySelector($m(i)), a || (e = x({
				src: e,
				async: !0
			}, t), (t = Pm.get(i)) && rh(e, t), a = n.createElement("script"), Xt(a), yp(a, "link", e), n.head.appendChild(a)), a = {
				type: "script",
				instance: a,
				count: 1,
				state: null
			}, r.set(i, a));
		}
	}
	function Jm(e, t) {
		Lm.M(e, t);
		var n = Bm;
		if (n && e) {
			var r = Yt(n).hoistableScripts, i = Qm(e), a = r.get(i);
			a || (a = n.querySelector($m(i)), a || (e = x({
				src: e,
				async: !0,
				type: "module"
			}, t), (t = Pm.get(i)) && rh(e, t), a = n.createElement("script"), Xt(a), yp(a, "link", e), n.head.appendChild(a)), a = {
				type: "script",
				instance: a,
				count: 1,
				state: null
			}, r.set(i, a));
		}
	}
	function R(e, t, n, r) {
		var i = (i = Le.current) ? Im(i) : null;
		if (!i) throw Error(a(446));
		switch (e) {
			case "meta":
			case "title": return null;
			case "style": return typeof n.precedence == "string" && typeof n.href == "string" ? (n = z(n.href), t = Yt(i).hoistableStyles, r = t.get(n), r || (r = {
				type: "style",
				instance: null,
				count: 0,
				state: null
			}, t.set(n, r)), r) : {
				type: "void",
				instance: null,
				count: 0,
				state: null
			};
			case "link":
				if (n.rel === "stylesheet" && typeof n.href == "string" && typeof n.precedence == "string") {
					e = z(n.href);
					var o = Yt(i).hoistableStyles, s = o.get(e);
					if (s || (i = i.ownerDocument || i, s = {
						type: "stylesheet",
						instance: null,
						count: 0,
						state: {
							loading: 0,
							preload: null
						}
					}, o.set(e, s), (o = i.querySelector(Ym(e))) ? o._p || (s.instance = o, s.state.loading = 5) : (o = Pm.get(e), o || (o = {
						rel: "preload",
						as: "style",
						href: n.href,
						crossOrigin: n.crossOrigin,
						integrity: n.integrity,
						media: n.media,
						hrefLang: n.hrefLang,
						referrerPolicy: n.referrerPolicy
					}, Pm.set(e, o)), Zm(i, e, o, s.state))), t && r === null) throw Error(a(528, ""));
					return s;
				}
				if (t && r !== null) throw Error(a(529, ""));
				return null;
			case "script": return t = n.async, n = n.src, typeof n == "string" && t && typeof t != "function" && typeof t != "symbol" ? (n = Qm(n), t = Yt(i).hoistableScripts, r = t.get(n), r || (r = {
				type: "script",
				instance: null,
				count: 0,
				state: null
			}, t.set(n, r)), r) : {
				type: "void",
				instance: null,
				count: 0,
				state: null
			};
			default: throw Error(a(444, e));
		}
	}
	function z(e) {
		return "href=\"" + _n(e) + "\"";
	}
	function Ym(e) {
		return "link[rel=\"stylesheet\"][" + e + "]";
	}
	function Xm(e) {
		return x({}, e, {
			"data-precedence": e.precedence,
			precedence: null
		});
	}
	function Zm(e, t, n, r) {
		if (t = e.querySelector("link[rel=\"preload\"][as=\"style\"][" + t + "]")) {
			if (!0 !== t[Wt]) {
				r.loading = 1;
				return;
			}
		} else t = e.createElement("link"), t[Wt] = !0, t.onload = t.onerror = Zt.bind(null, t), yp(t, "link", n), Xt(t), e.head.appendChild(t);
		r.preload = t, t.addEventListener("load", function() {
			return r.loading |= 1;
		}), t.addEventListener("error", function() {
			return r.loading |= 2;
		});
	}
	function Qm(e) {
		return "[src=\"" + _n(e) + "\"]";
	}
	function $m(e) {
		return "script[async]" + e;
	}
	function eh(e, t, n) {
		if (t.count++, t.instance === null) switch (t.type) {
			case "style":
				var r = e.querySelector("style[data-href~=\"" + _n(n.href) + "\"]");
				if (r) return t.instance = r, Xt(r), r;
				var i = x({}, n, {
					"data-href": n.href,
					"data-precedence": n.precedence,
					href: null,
					precedence: null
				});
				return r = (e.ownerDocument || e).createElement("style"), Xt(r), yp(r, "style", i), th(r, n.precedence, e), t.instance = r;
			case "stylesheet":
				i = z(n.href);
				var o = e.querySelector(Ym(i));
				if (o) return t.state.loading |= 4, t.instance = o, Xt(o), o;
				r = Xm(n), (i = Pm.get(i)) && nh(r, i), o = (e.ownerDocument || e).createElement("link"), Xt(o);
				var s = o;
				return s._p = new Promise(function(e, t) {
					s.onload = e, s.onerror = t;
				}), yp(o, "link", r), t.state.loading |= 4, th(o, n.precedence, e), t.instance = o;
			case "script": return o = Qm(n.src), (i = e.querySelector($m(o))) ? (t.instance = i, Xt(i), i) : (r = n, (i = Pm.get(o)) && (r = x({}, n), rh(r, i)), e = e.ownerDocument || e, i = e.createElement("script"), Xt(i), yp(i, "link", r), e.head.appendChild(i), t.instance = i);
			case "void": return null;
			default: throw Error(a(443, t.type));
		}
		else t.type === "stylesheet" && !(t.state.loading & 4) && (r = t.instance, t.state.loading |= 4, th(r, n.precedence, e));
		return t.instance;
	}
	function th(e, t, n) {
		for (var r = n.querySelectorAll("link[rel=\"stylesheet\"][data-precedence],style[data-precedence]"), i = r.length ? r[r.length - 1] : null, a = i, o = 0; o < r.length; o++) {
			var s = r[o];
			if (s.dataset.precedence === t) a = s;
			else if (a !== i) break;
		}
		a ? a.parentNode.insertBefore(e, a.nextSibling) : (t = n.nodeType === 9 ? n.head : n, t.insertBefore(e, t.firstChild));
	}
	function nh(e, t) {
		e.crossOrigin ??= t.crossOrigin, e.referrerPolicy ??= t.referrerPolicy, e.title ??= t.title;
	}
	function rh(e, t) {
		e.crossOrigin ??= t.crossOrigin, e.referrerPolicy ??= t.referrerPolicy, e.integrity ??= t.integrity;
	}
	var ih = null;
	function ah(e, t, n) {
		if (ih === null) {
			var r = /* @__PURE__ */ new Map(), i = ih = /* @__PURE__ */ new Map();
			i.set(n, r);
		} else i = ih, r = i.get(n), r || (r = /* @__PURE__ */ new Map(), i.set(n, r));
		if (r.has(e)) return r;
		for (r.set(e, null), n = n.getElementsByTagName(e), i = 0; i < n.length; i++) {
			var a = n[i];
			if (!(a[Ut] || a[It] || e === "link" && a.getAttribute("rel") === "stylesheet") && a.namespaceURI !== "http://www.w3.org/2000/svg") {
				var o = a.getAttribute(t) || "";
				o = e + o;
				var s = r.get(o);
				s ? s.push(a) : r.set(o, [a]);
			}
		}
		return r;
	}
	function oh(e, t, n) {
		e = e.ownerDocument || e, e.head.insertBefore(n, t === "title" ? e.querySelector("head > title") : null);
	}
	function sh(e, t, n) {
		if (n === 1 || t.itemProp != null) return !1;
		switch (e) {
			case "meta":
			case "title": return !0;
			case "style":
				if (typeof t.precedence != "string" || typeof t.href != "string" || t.href === "") break;
				return !0;
			case "link":
				if (typeof t.rel != "string" || typeof t.href != "string" || t.href === "" || t.onLoad || t.onError) break;
				switch (t.rel) {
					case "stylesheet": return e = t.disabled, typeof t.precedence == "string" && e == null;
					default: return !0;
				}
			case "script": if (t.async && typeof t.async != "function" && typeof t.async != "symbol" && !t.onLoad && !t.onError && t.src && typeof t.src == "string") return !0;
		}
		return !1;
	}
	function ch(e, t) {
		return e === "img" && t.src != null && t.src !== "" && t.onLoad == null && t.loading !== "lazy";
	}
	function lh(e) {
		return !(e.type === "stylesheet" && !(e.state.loading & 3));
	}
	function uh(e) {
		return (e.width || 100) * (e.height || 100) * (typeof devicePixelRatio == "number" ? devicePixelRatio : 1) * .25;
	}
	function dh(e, t) {
		typeof t.decode == "function" && (e.imgCount++, t.complete || (e.imgBytes += uh(t), e.suspenseyImages.push(t)), e = _h.bind(e), t.decode().then(e, e));
	}
	function fh(e, t, n, r) {
		if (n.type === "stylesheet" && (typeof r.media != "string" || !1 !== matchMedia(r.media).matches) && !(n.state.loading & 4)) {
			if (n.instance === null) {
				var i = z(r.href), a = t.querySelector(Ym(i));
				if (a) {
					t = a._p, typeof t == "object" && t && typeof t.then == "function" && (e.count++, e = gh.bind(e), t.then(e, e)), n.state.loading |= 4, n.instance = a, Xt(a);
					return;
				}
				a = t.ownerDocument || t, r = Xm(r), (i = Pm.get(i)) && nh(r, i), a = a.createElement("link"), Xt(a);
				var o = a;
				o._p = new Promise(function(e, t) {
					o.onload = e, o.onerror = t;
				}), yp(a, "link", r), n.instance = a;
			}
			e.stylesheets === null && (e.stylesheets = /* @__PURE__ */ new Map()), e.stylesheets.set(n, t), (t = n.state.preload) && !(n.state.loading & 3) && (e.count++, n = gh.bind(e), t.addEventListener("load", n), t.addEventListener("error", n));
		}
	}
	var ph = 0;
	function mh(e, t) {
		return e.stylesheets && e.count === 0 && yh(e, e.stylesheets), 0 < e.count || 0 < e.imgCount ? function(n) {
			var r = setTimeout(function() {
				if (e.stylesheets && yh(e, e.stylesheets), e.unsuspend) {
					var t = e.unsuspend;
					e.unsuspend = null, t();
				}
			}, 6e4 + t);
			0 < e.imgBytes && ph === 0 && (ph = 62500 * Cp());
			var i = setTimeout(function() {
				if (e.waitingForImages = !1, e.count === 0 && (e.stylesheets && yh(e, e.stylesheets), e.unsuspend)) {
					var t = e.unsuspend;
					e.unsuspend = null, t();
				}
			}, (e.imgBytes > ph ? 50 : 800) + t);
			return e.unsuspend = n, function() {
				e.unsuspend = null, clearTimeout(r), clearTimeout(i);
			};
		} : null;
	}
	function hh(e) {
		if (e.count === 0 && (e.imgCount === 0 || !e.waitingForImages)) {
			if (e.stylesheets) yh(e, e.stylesheets);
			else if (e.unsuspend) {
				var t = e.unsuspend;
				e.unsuspend = null, t();
			}
		}
	}
	function gh() {
		this.count--, hh(this);
	}
	function _h() {
		this.imgCount--, hh(this);
	}
	var vh = null;
	function yh(e, t) {
		e.stylesheets = null, e.unsuspend !== null && (e.count++, vh = /* @__PURE__ */ new Map(), t.forEach(bh, e), vh = null, gh.call(e));
	}
	function bh(e, t) {
		if (!(t.state.loading & 4)) {
			var n = vh.get(e);
			if (n) var r = n.get(null);
			else {
				n = /* @__PURE__ */ new Map(), vh.set(e, n);
				for (var i = e.querySelectorAll("link[data-precedence],style[data-precedence]"), a = 0; a < i.length; a++) {
					var o = i[a];
					(o.nodeName === "LINK" || o.getAttribute("media") !== "not all") && (n.set(o.dataset.precedence, o), r = o);
				}
				r && n.set(null, r);
			}
			i = t.instance, o = i.getAttribute("data-precedence"), a = n.get(o) || r, a === r && n.set(null, i), n.set(o, i), this.count++, r = gh.bind(this), i.addEventListener("load", r), i.addEventListener("error", r), a ? a.parentNode.insertBefore(i, a.nextSibling) : (e = e.nodeType === 9 ? e.head : e, e.insertBefore(i, e.firstChild)), t.state.loading |= 4;
		}
	}
	var xh = {
		$$typeof: pe,
		Provider: null,
		Consumer: null,
		_currentValue: ke,
		_currentValue2: ke,
		_threadCount: 0
	};
	function Sh(e, t, n, r, i, a, o, s, c) {
		this.tag = 1, this.containerInfo = e, this.pingCache = this.current = this.pendingChildren = null, this.timeoutHandle = -1, this.callbackNode = this.next = this.pendingContext = this.context = this.cancelPendingCommit = null, this.callbackPriority = 0, this.expirationTimes = Tt(-1), this.entangledLanes = this.shellSuspendCounter = this.errorRecoveryDisabledLanes = this.expiredLanes = this.warmLanes = this.pingedLanes = this.suspendedLanes = this.pendingLanes = 0, this.entanglements = Tt(0), this.hiddenUpdates = Tt(null), this.identifierPrefix = r, this.onUncaughtError = i, this.onCaughtError = a, this.onRecoverableError = o, this.pooledCache = null, this.pooledCacheLanes = 0, this.formState = c, this.transitionTypes = null, this.incompleteTransitions = /* @__PURE__ */ new Map();
	}
	function Ch(e, t, n, r, i, a, o, s, c, l, u, d) {
		return e = new Sh(e, t, n, o, c, l, u, d, s), t = 1, !0 === a && (t |= 24), a = Ki(3, null, null, t), e.current = a, a.stateNode = e, t = O(), t.refCount++, e.pooledCache = t, t.refCount++, a.memoizedState = {
			element: r,
			isDehydrated: n,
			cache: t
		}, Eo(a), e;
	}
	function wh(e) {
		return e ? (e = Wi, e) : Wi;
	}
	function Th(e, t, n, r, i, a) {
		i = wh(i), r.context === null ? r.context = i : r.pendingContext = i, r = Oo(t), r.payload = { element: n }, a = a === void 0 ? null : a, a !== null && (r.callback = a), n = ko(e, r, t), n !== null && (Yd(n, e, t), Ao(n, e, t));
	}
	function Eh(e, t) {
		if (e = e.memoizedState, e !== null && e.dehydrated !== null) {
			var n = e.retryLane;
			e.retryLane = n !== 0 && n < t ? n : t;
		}
	}
	function Dh(e, t) {
		Eh(e, t), (e = e.alternate) && Eh(e, t);
	}
	function Oh(e) {
		if (e.tag === 13 || e.tag === 31) {
			var t = Vi(e, 67108864);
			t !== null && Yd(t, e, 67108864), Dh(e, 67108864);
		}
	}
	function kh(e) {
		if (e.tag === 13 || e.tag === 31) {
			var t = Kd();
			t = jt(t);
			var n = Vi(e, t);
			n !== null && Yd(n, e, t), Dh(e, t);
		}
	}
	var Ah = !0;
	function jh(e, t, n, r) {
		var i = C.T;
		C.T = null;
		var a = Oe.p;
		try {
			Oe.p = 2, Nh(e, t, n, r);
		} finally {
			Oe.p = a, C.T = i;
		}
	}
	function Mh(e, t, n, r) {
		var i = C.T;
		C.T = null;
		var a = Oe.p;
		try {
			Oe.p = 8, Nh(e, t, n, r);
		} finally {
			Oe.p = a, C.T = i;
		}
	}
	function Nh(e, t, n, r) {
		if (Ah) {
			var i = Ph(r);
			if (i === null) cp(e, t, r, Fh, n), Kh(e, r);
			else if (Jh(i, e, t, n, r)) r.stopPropagation();
			else if (Kh(e, r), t & 4 && -1 < Gh.indexOf(e)) {
				for (; i !== null;) {
					var a = qt(i);
					if (a !== null) switch (a.tag) {
						case 3:
							if (a = a.stateNode, a.current.memoizedState.isDehydrated) {
								var o = yt(a.pendingLanes);
								if (o !== 0) {
									var s = a;
									for (s.pendingLanes |= 2, s.entangledLanes |= 2; o;) {
										var c = 1 << 31 - ft(o);
										s.entanglements[1] |= c, o &= ~c;
									}
									Hf(a), !(md & 6) && (Md = et() + 500, Uf(0, !1));
								}
							}
							break;
						case 31:
						case 13: s = Vi(a, 2), s !== null && Yd(s, a, 2), ef(), Dh(a, 2);
					}
					if (a = Ph(r), a === null && cp(e, t, r, Fh, n), a === i) break;
					i = a;
				}
				i !== null && r.stopPropagation();
			} else cp(e, t, r, null, n);
		}
	}
	function Ph(e) {
		return e = Pn(e), Ih(e);
	}
	var Fh = null;
	function Ih(e) {
		if (Fh = null, e = Kt(e), e !== null) {
			var t = c(e);
			if (t === null) e = null;
			else {
				var n = t.tag;
				if (n === 13) {
					if (e = u(t), e !== null) return e;
					e = null;
				} else if (n === 31) {
					if (e = d(t), e !== null) return e;
					e = null;
				} else if (n === 3) {
					if (t.stateNode.current.memoizedState.isDehydrated) return t.tag === 3 ? t.stateNode.containerInfo : null;
					e = null;
				} else t !== e && (e = null);
			}
		}
		return Fh = e, null;
	}
	function Lh(e) {
		switch (e) {
			case "beforetoggle":
			case "cancel":
			case "click":
			case "close":
			case "contextmenu":
			case "copy":
			case "cut":
			case "auxclick":
			case "dblclick":
			case "dragend":
			case "dragstart":
			case "drop":
			case "focusin":
			case "focusout":
			case "input":
			case "invalid":
			case "keydown":
			case "keypress":
			case "keyup":
			case "mousedown":
			case "mouseup":
			case "paste":
			case "pause":
			case "play":
			case "pointercancel":
			case "pointerdown":
			case "pointerup":
			case "ratechange":
			case "reset":
			case "seeked":
			case "submit":
			case "toggle":
			case "touchcancel":
			case "touchend":
			case "touchstart":
			case "volumechange":
			case "change":
			case "selectionchange":
			case "textInput":
			case "compositionstart":
			case "compositionend":
			case "compositionupdate":
			case "beforeblur":
			case "afterblur":
			case "beforeinput":
			case "blur":
			case "fullscreenchange":
			case "fullscreenerror":
			case "focus":
			case "hashchange":
			case "popstate":
			case "select":
			case "selectstart": return 2;
			case "drag":
			case "dragenter":
			case "dragexit":
			case "dragleave":
			case "dragover":
			case "mousemove":
			case "mouseout":
			case "mouseover":
			case "pointermove":
			case "pointerout":
			case "pointerover":
			case "resize":
			case "scroll":
			case "touchmove":
			case "wheel":
			case "mouseenter":
			case "mouseleave":
			case "pointerenter":
			case "pointerleave": return 8;
			case "message": switch (tt()) {
				case nt: return 2;
				case rt: return 8;
				case it:
				case at: return 32;
				case ot: return 268435456;
				default: return 32;
			}
			default: return 32;
		}
	}
	var Rh = !1, zh = null, Bh = null, Vh = null, Hh = /* @__PURE__ */ new Map(), Uh = /* @__PURE__ */ new Map(), Wh = [], Gh = "mousedown mouseup touchcancel touchend touchstart auxclick dblclick pointercancel pointerdown pointerup dragend dragstart drop compositionend compositionstart keydown keypress keyup input textInput copy cut paste click change contextmenu reset".split(" ");
	function Kh(e, t) {
		switch (e) {
			case "focusin":
			case "focusout":
				zh = null;
				break;
			case "dragenter":
			case "dragleave":
				Bh = null;
				break;
			case "mouseover":
			case "mouseout":
				Vh = null;
				break;
			case "pointerover":
			case "pointerout":
				Hh.delete(t.pointerId);
				break;
			case "gotpointercapture":
			case "lostpointercapture": Uh.delete(t.pointerId);
		}
	}
	function qh(e, t, n, r, i, a) {
		return e === null || e.nativeEvent !== a ? (e = {
			blockedOn: t,
			domEventName: n,
			eventSystemFlags: r,
			nativeEvent: a,
			targetContainers: [i]
		}, t !== null && (t = qt(t), t !== null && Oh(t)), e) : (e.eventSystemFlags |= r, t = e.targetContainers, i !== null && t.indexOf(i) === -1 && t.push(i), e);
	}
	function Jh(e, t, n, r, i) {
		switch (t) {
			case "focusin": return zh = qh(zh, e, t, n, r, i), !0;
			case "dragenter": return Bh = qh(Bh, e, t, n, r, i), !0;
			case "mouseover": return Vh = qh(Vh, e, t, n, r, i), !0;
			case "pointerover":
				var a = i.pointerId;
				return Hh.set(a, qh(Hh.get(a) || null, e, t, n, r, i)), !0;
			case "gotpointercapture": return a = i.pointerId, Uh.set(a, qh(Uh.get(a) || null, e, t, n, r, i)), !0;
		}
		return !1;
	}
	function Yh(e) {
		var t = Kt(e.target);
		if (t !== null) {
			var n = c(t);
			if (n !== null) {
				if (t = n.tag, t === 13) {
					if (t = u(n), t !== null) {
						e.blockedOn = t, Pt(e.priority, function() {
							kh(n);
						});
						return;
					}
				} else if (t === 31) {
					if (t = d(n), t !== null) {
						e.blockedOn = t, Pt(e.priority, function() {
							kh(n);
						});
						return;
					}
				} else if (t === 3 && n.stateNode.current.memoizedState.isDehydrated) {
					e.blockedOn = n.tag === 3 ? n.stateNode.containerInfo : null;
					return;
				}
			}
		}
		e.blockedOn = null;
	}
	function Xh(e) {
		if (e.blockedOn !== null) return !1;
		for (var t = e.targetContainers; 0 < t.length;) {
			var n = Ph(e.nativeEvent);
			if (n === null) {
				n = e.nativeEvent;
				var r = new n.constructor(n.type, n);
				Nn = r, n.target.dispatchEvent(r), Nn = null;
			} else return t = qt(n), t !== null && Oh(t), e.blockedOn = n, !1;
			t.shift();
		}
		return !0;
	}
	function Zh(e, t, n) {
		Xh(e) && n.delete(t);
	}
	function Qh() {
		Rh = !1, zh !== null && Xh(zh) && (zh = null), Bh !== null && Xh(Bh) && (Bh = null), Vh !== null && Xh(Vh) && (Vh = null), Hh.forEach(Zh), Uh.forEach(Zh);
	}
	function $h(e, t) {
		e.blockedOn === t && (e.blockedOn = null, Rh || (Rh = !0, n.unstable_scheduleCallback(n.unstable_NormalPriority, Qh)));
	}
	var eg = null;
	function tg(e) {
		eg !== e && (eg = e, n.unstable_scheduleCallback(n.unstable_NormalPriority, function() {
			eg === e && (eg = null);
			for (var t = 0; t < e.length; t += 3) {
				var n = e[t], r = e[t + 1], i = e[t + 2];
				if (typeof r != "function") {
					if (Ih(r || n) === null) continue;
					break;
				}
				var a = qt(n);
				a !== null && (e.splice(t, 3), t -= 3, pc(a, {
					pending: !0,
					data: i,
					method: n.method,
					action: r
				}, r, i));
			}
		}));
	}
	function ng(e) {
		function t(t) {
			return $h(t, e);
		}
		zh !== null && $h(zh, e), Bh !== null && $h(Bh, e), Vh !== null && $h(Vh, e), Hh.forEach(t), Uh.forEach(t);
		for (var n = 0; n < Wh.length; n++) {
			var r = Wh[n];
			r.blockedOn === e && (r.blockedOn = null);
		}
		for (; 0 < Wh.length && (n = Wh[0], n.blockedOn === null);) Yh(n), n.blockedOn === null && Wh.shift();
		if (n = (e.ownerDocument || e).$$reactFormReplay, n != null) for (r = 0; r < n.length; r += 3) {
			var i = n[r], a = n[r + 1], o = i[Lt] || null;
			if (typeof a == "function") o || tg(n);
			else if (o) {
				var s = null;
				if (a && a.hasAttribute("formAction")) {
					if (i = a, o = a[Lt] || null) s = o.formAction;
					else if (Ih(i) !== null) continue;
				} else s = o.action;
				typeof s == "function" ? n[r + 1] = s : (n.splice(r, 3), r -= 3), tg(n);
			}
		}
	}
	function rg() {
		function e(e) {
			e.canIntercept && e.info === "react-transition" && e.intercept({
				handler: function() {
					return new Promise(function(e) {
						return i = e;
					});
				},
				focusReset: "manual",
				scroll: "manual"
			});
		}
		function t() {
			i !== null && (i(), i = null), r || setTimeout(n, 20);
		}
		function n() {
			if (!r && !navigation.transition) {
				var e = navigation.currentEntry;
				e && e.url != null && navigation.navigate(e.url, {
					state: e.getState(),
					info: "react-transition",
					history: "replace"
				});
			}
		}
		if (typeof navigation == "object") {
			var r = !1, i = null;
			return navigation.addEventListener("navigate", e), navigation.addEventListener("navigatesuccess", t), navigation.addEventListener("navigateerror", t), setTimeout(n, 100), function() {
				r = !0, navigation.removeEventListener("navigate", e), navigation.removeEventListener("navigatesuccess", t), navigation.removeEventListener("navigateerror", t), i !== null && (i(), i = null);
			};
		}
	}
	function ig(e) {
		this._internalRoot = e;
	}
	ag.prototype.render = ig.prototype.render = function(e) {
		var t = this._internalRoot;
		if (t === null) throw Error(a(409));
		var n = t.current;
		Th(n, Kd(), e, t, null, null);
	}, ag.prototype.unmount = ig.prototype.unmount = function() {
		var e = this._internalRoot;
		if (e !== null) {
			this._internalRoot = null;
			var t = e.containerInfo;
			Th(e.current, 2, null, e, null, null), ef(), t[Rt] = null;
		}
	};
	function ag(e) {
		this._internalRoot = e;
	}
	ag.prototype.unstable_scheduleHydration = function(e) {
		if (e) {
			var t = Nt();
			e = {
				blockedOn: null,
				target: e,
				priority: t
			};
			for (var n = 0; n < Wh.length && t !== 0 && t < Wh[n].priority; n++);
			Wh.splice(n, 0, e), n === 0 && Yh(e);
		}
	};
	var og = r.version;
	if (og !== "19.3.1") throw Error(a(527, og, "19.3.1"));
	Oe.findDOMNode = function(e) {
		var t = e._reactInternals;
		if (t === void 0) throw typeof e.render == "function" ? Error(a(188)) : (e = Object.keys(e).join(","), Error(a(268, e)));
		return e = p(t), e = e === null ? null : m(e), e = e === null ? null : e.stateNode, e;
	};
	var sg = {
		bundleType: 0,
		version: "19.3.1",
		rendererPackageName: "react-dom",
		currentDispatcherRef: C,
		reconcilerVersion: "19.3.1"
	};
	if (typeof __REACT_DEVTOOLS_GLOBAL_HOOK__ < "u") {
		var B = __REACT_DEVTOOLS_GLOBAL_HOOK__;
		if (!B.isDisabled && B.supportsFiber) try {
			lt = B.inject(sg), ut = B;
		} catch {}
	}
	e.createRoot = function(e, t) {
		if (!s(e)) throw Error(a(299));
		var n = !1, r = "", i = Ic, o = Lc, c = Rc;
		return t != null && (!0 === t.unstable_strictMode && (n = !0), t.identifierPrefix !== void 0 && (r = t.identifierPrefix), t.onUncaughtError !== void 0 && (i = t.onUncaughtError), t.onCaughtError !== void 0 && (o = t.onCaughtError), t.onRecoverableError !== void 0 && (c = t.onRecoverableError)), t = Ch(e, 1, !1, null, null, n, r, null, i, o, c, rg), e[Rt] = t.current, op(e), new ig(t);
	}, e.hydrateRoot = function(e, t, n) {
		if (!s(e)) throw Error(a(299));
		var r = !1, i = "", o = Ic, c = Lc, l = Rc, u = null;
		return n != null && (!0 === n.unstable_strictMode && (r = !0), n.identifierPrefix !== void 0 && (i = n.identifierPrefix), n.onUncaughtError !== void 0 && (o = n.onUncaughtError), n.onCaughtError !== void 0 && (c = n.onCaughtError), n.onRecoverableError !== void 0 && (l = n.onRecoverableError), n.formState !== void 0 && (u = n.formState)), t = Ch(e, 1, !0, t, n ?? null, r, i, u, o, c, l, rg), t.context = wh(null), n = t.current, r = Kd(), r = jt(r), i = Oo(r), i.callback = null, ko(n, i, r), n = r, t.current.lanes = n, Et(t, n), Hf(t), e[Rt] = t.current, op(e), new ag(t);
	}, e.version = "19.3.1";
})), d = /* @__PURE__ */ n(((e) => {
	process.env.NODE_ENV !== "production" && (function() {
		function n(e, t) {
			for (e = e.memoizedState; e !== null && 0 < t;) e = e.next, t--;
			return e;
		}
		function r(e, t, n, i) {
			if (n >= t.length) return i;
			var a = t[n], o = Jm(e) ? e.slice() : L({}, e);
			return o[a] = r(e[a], t, n + 1, i), o;
		}
		function i(e, t, n) {
			if (t.length !== n.length) console.warn("copyWithRename() expects paths of the same length");
			else {
				for (var r = 0; r < n.length - 1; r++) if (t[r] !== n[r]) {
					console.warn("copyWithRename() expects paths to be the same except for the deepest key");
					return;
				}
				return a(e, t, n, 0);
			}
		}
		function a(e, t, n, r) {
			var i = t[r], o = Jm(e) ? e.slice() : L({}, e);
			return r + 1 === t.length ? (o[n[r]] = o[i], Jm(o) ? o.splice(i, 1) : delete o[i]) : o[i] = a(e[i], t, n, r + 1), o;
		}
		function s(e, t, n) {
			var r = t[n], i = Jm(e) ? e.slice() : L({}, e);
			return n + 1 === t.length ? (Jm(i) ? i.splice(r, 1) : delete i[r], i) : (i[r] = s(e[r], t, n + 1), i);
		}
		function c() {
			return !1;
		}
		function u() {
			return null;
		}
		function d() {
			console.error("Do not call Hooks inside useEffect(...), useMemo(...), or other built-in Hooks. You can only call Hooks at the top level of your React function. For more information, see https://react.dev/link/rules-of-hooks");
		}
		function f() {
			console.error("Context can only be read while React is rendering. In classes, you can read it in the render method or getDerivedStateFromProps. In function components, you can read it directly in the function body, but not inside Hooks like useReducer() or useMemo().");
		}
		function p() {}
		function m() {}
		function h(e) {
			var t = [];
			return e.forEach(function(e) {
				t.push(e);
			}), t.sort().join(", ");
		}
		function g(e, t, n, r) {
			return new Hr(e, t, n, r);
		}
		function _(e, t) {
			e.context === Rv && (Zp(e.current, 2, t, e, null, null), fu());
		}
		function v(e, t) {
			if (zv !== null) {
				var n = t.staleFamilies;
				t = t.updatedFamilies, Uu(), Vr(e.current, t, n), fu();
			}
		}
		function y(e) {
			zv = e;
		}
		function b(e) {
			return !(!e || e.nodeType !== 1 && e.nodeType !== 9 && e.nodeType !== 11);
		}
		function ee(e) {
			for (var t = e, n = t; n && !n.alternate;) t = n, t.flags & 4098 && (e = t.return), n = t.return;
			for (; t.return;) t = t.return;
			return t.tag === 3 ? e : null;
		}
		function te(e) {
			if (e.tag === 13) {
				var t = e.memoizedState;
				if (t === null && (e = e.alternate, e !== null && (t = e.memoizedState)), t !== null) return t.dehydrated;
			}
			return null;
		}
		function ne(e) {
			if (e.tag === 31) {
				var t = e.memoizedState;
				if (t === null && (e = e.alternate, e !== null && (t = e.memoizedState)), t !== null) return t.dehydrated;
			}
			return null;
		}
		function re(e) {
			if (ee(e) !== e) throw Error("Unable to find node on an unmounted component.");
		}
		function ie(e) {
			var t = e.alternate;
			if (!t) {
				if (t = ee(e), t === null) throw Error("Unable to find node on an unmounted component.");
				return t === e ? e : null;
			}
			for (var n = e, r = t;;) {
				var i = n.return;
				if (i === null) break;
				var a = i.alternate;
				if (a === null) {
					if (r = i.return, r !== null) {
						n = r;
						continue;
					}
					break;
				}
				if (i.child === a.child) {
					for (a = i.child; a;) {
						if (a === n) return re(i), e;
						if (a === r) return re(i), t;
						a = a.sibling;
					}
					throw Error("Unable to find node on an unmounted component.");
				}
				if (n.return !== r.return) n = i, r = a;
				else {
					for (var o = !1, s = i.child; s;) {
						if (s === n) {
							o = !0, n = i, r = a;
							break;
						}
						if (s === r) {
							o = !0, r = i, n = a;
							break;
						}
						s = s.sibling;
					}
					if (!o) {
						for (s = a.child; s;) {
							if (s === n) {
								o = !0, n = a, r = i;
								break;
							}
							if (s === r) {
								o = !0, r = a, n = i;
								break;
							}
							s = s.sibling;
						}
						if (!o) throw Error("Child was not found in either parent set. This indicates a bug in React related to the return pointer. Please file an issue.");
					}
				}
				if (n.alternate !== r) throw Error("Return fibers should always be each others' alternates. This error is likely caused by a bug in React. Please file an issue.");
			}
			if (n.tag !== 3) throw Error("Unable to find node on an unmounted component.");
			return n.stateNode.current === n ? e : t;
		}
		function ae(e) {
			var t = e.tag;
			if (t === 5 || t === 26 || t === 27 || t === 6) return e;
			for (e = e.child; e !== null;) {
				if (t = ae(e), t !== null) return t;
				e = e.sibling;
			}
			return null;
		}
		function x(e, t, n, r, i) {
			oe(e.child, !1, t, n, r, i);
		}
		function oe(e, t, n, r, i, a) {
			for (; e !== null;) {
				if ((e.tag === 5 || e.tag === 27 || e.tag === 6) && n(e, r, i, a) || (e.tag !== 22 || e.memoizedState === null) && (t || e.tag !== 5 && e.tag !== 27) && oe(e.child, t, n, r, i, a)) return !0;
				e = e.sibling;
			}
			return !1;
		}
		function se(e) {
			for (e = e.return; e !== null;) {
				if (e.tag === 3 || e.tag === 5 || e.tag === 27) return e;
				e = e.return;
			}
			return null;
		}
		function ce(e) {
			var t = !1;
			for (e = e.return; e !== null && (e.tag === 4 && (t = !0), e.tag !== 3 && e.tag !== 5 && e.tag !== 27);) e = e.return;
			return t;
		}
		function le(e) {
			var t = [null, null], n = se(e);
			return n === null || ue(t, e, n.child, { foundSelf: !1 }), t;
		}
		function ue(e, t, n, r) {
			for (; n !== null;) {
				if (n === t) r.foundSelf = !0;
				else if (n.tag === 5 || n.tag === 27 || n.tag === 6) {
					if (r.foundSelf) return e[1] = n, !0;
					e[0] = n;
				} else if ((n.tag !== 22 || n.memoizedState === null) && ue(e, t, n.child, r)) return !0;
				n = n.sibling;
			}
			return !1;
		}
		function de(e) {
			switch (e.tag) {
				case 5:
				case 27:
				case 6: return e.stateNode;
				case 3: return e.stateNode.containerInfo;
				default: throw Error("Expected to find a host node. This is a bug in React.");
			}
		}
		function fe(e, t, n) {
			return e === n || e === t && (Em = e, !0);
		}
		function pe(e, t, n) {
			return e === n ? (Dm = e, !1) : e === t && (Dm !== null && (Em = e), !0);
		}
		function me(e) {
			if (e === null) return null;
			do
				e = e === null ? null : e.return;
			while (e && e.tag !== 5 && e.tag !== 27 && e.tag !== 3);
			return e || null;
		}
		function he(e, t, n) {
			for (var r = 0, i = e; i; i = n(i)) r++;
			i = 0;
			for (var a = t; a; a = n(a)) i++;
			for (; 0 < r - i;) e = n(e), r--;
			for (; 0 < i - r;) t = n(t), i--;
			for (; r--;) {
				if (e === t || t !== null && e === t.alternate) return e;
				e = n(e), t = n(t);
			}
			return null;
		}
		function ge(e) {
			return typeof e != "object" || !e ? null : (e = Km && e[Km] || e["@@iterator"], typeof e == "function" ? e : null);
		}
		function _e(e) {
			if (e == null) return null;
			if (typeof e == "function") return e.$$typeof === qm ? null : e.displayName || e.name || null;
			if (typeof e == "string") return e;
			switch (e) {
				case jm: return "Fragment";
				case Nm: return "Profiler";
				case Mm: return "StrictMode";
				case Lm: return "Suspense";
				case Rm: return "SuspenseList";
				case Vm: return "Activity";
				case Wm: return "ViewTransition";
			}
			if (typeof e == "object") switch (typeof e.tag == "number" && console.error("Received an unexpected object in getComponentNameFromType(). This is likely a bug in React. Please file an issue."), e.$$typeof) {
				case Am: return "Portal";
				case Fm: return e.displayName || "Context";
				case Pm: return (e._context.displayName || "Context") + ".Consumer";
				case Im:
					var t = e.render;
					return e = e.displayName, e ||= (e = t.displayName || t.name || "", e === "" ? "ForwardRef" : "ForwardRef(" + e + ")"), e;
				case zm: return t = e.displayName || null, t === null ? _e(e.type) || "Memo" : t;
				case Bm:
					t = e._payload, e = e._init;
					try {
						return _e(e(t));
					} catch {}
			}
			return null;
		}
		function ve(e) {
			return typeof e.tag == "number" ? S(e) : typeof e.name == "string" ? e.name : null;
		}
		function S(e) {
			var t = e.type;
			switch (e.tag) {
				case 31: return "Activity";
				case 24: return "Cache";
				case 9: return (t._context.displayName || "Context") + ".Consumer";
				case 10: return t.displayName || "Context";
				case 18: return "DehydratedFragment";
				case 11: return e = t.render, e = e.displayName || e.name || "", t.displayName || (e === "" ? "ForwardRef" : "ForwardRef(" + e + ")");
				case 7: return "Fragment";
				case 26:
				case 27:
				case 5: return t;
				case 4: return "Portal";
				case 3: return "Root";
				case 6: return "Text";
				case 16: return _e(t);
				case 8: return t === Mm ? "StrictMode" : "Mode";
				case 22:
					if (e.return !== null) return S(e.return);
					break;
				case 12: return "Profiler";
				case 21: return "Scope";
				case 13: return "Suspense";
				case 19: return "SuspenseList";
				case 25: return "TracingMarker";
				case 30: return "ViewTransition";
				case 1:
				case 0:
				case 14:
				case 15:
					if (typeof t == "function") return t.displayName || t.name || null;
					if (typeof t == "string") return t;
					break;
				case 29:
					if (t = e._debugInfo, t != null) {
						for (var n = t.length - 1; 0 <= n; n--) if (typeof t[n].name == "string") return t[n].name;
					}
					if (e.return !== null) return S(e.return);
			}
			return null;
		}
		function ye(e) {
			return { current: e };
		}
		function be(e, t) {
			0 > Qm ? console.error("Unexpected pop.") : (t !== Zm[Qm] && console.error("Unexpected Fiber popped."), e.current = Xm[Qm], Xm[Qm] = null, Zm[Qm] = null, Qm--);
		}
		function xe(e, t, n) {
			Qm++, Xm[Qm] = e.current, Zm[Qm] = n, e.current = t;
		}
		function Se(e) {
			return e === null && console.error("Expected host context to exist. This error is likely caused by a bug in React. Please file an issue."), e;
		}
		function Ce(e, t) {
			xe(th, t, e), xe(eh, e, e), xe($m, null, e);
			var n = t.nodeType;
			switch (n) {
				case 9:
				case 11:
					n = n === 9 ? "#document" : "#fragment", t = (t = t.documentElement) && (t = t.namespaceURI) ? Xd(t) : _T;
					break;
				default: if (n = t.tagName, t = t.namespaceURI) t = Xd(t), t = Zd(t, n);
				else switch (n) {
					case "svg":
						t = vT;
						break;
					case "math":
						t = yT;
						break;
					default: t = _T;
				}
			}
			n = n.toLowerCase(), n = cn(null, n), n = {
				context: t,
				ancestorInfo: n
			}, be($m, e), xe($m, n, e);
		}
		function we(e) {
			be($m, e), be(eh, e), be(th, e);
		}
		function Te() {
			return Se($m.current);
		}
		function Ee(e) {
			var t = e.memoizedState;
			t !== null && ($T._currentValue = t.memoizedState, xe(nh, e, e)), t = Se($m.current);
			var n = e.type, r = Zd(t.context, n);
			n = cn(t.ancestorInfo, n), r = {
				context: r,
				ancestorInfo: n
			}, t !== r && (xe(eh, e, e), xe($m, r, e));
		}
		function De(e) {
			eh.current === e && (be($m, e), be(eh, e)), nh.current === e && (be(nh, e), $T._currentValue = QT);
		}
		function C() {}
		function Oe() {
			if (rh === 0) {
				ih = console.log, ah = console.info, oh = console.warn, sh = console.error, ch = console.group, lh = console.groupCollapsed, uh = console.groupEnd;
				var e = {
					configurable: !0,
					enumerable: !0,
					value: C,
					writable: !0
				};
				Object.defineProperties(console, {
					info: e,
					log: e,
					warn: e,
					error: e,
					group: e,
					groupCollapsed: e,
					groupEnd: e
				});
			}
			rh++;
		}
		function ke() {
			if (rh--, rh === 0) {
				var e = {
					configurable: !0,
					enumerable: !0,
					writable: !0
				};
				Object.defineProperties(console, {
					log: L({}, e, { value: ih }),
					info: L({}, e, { value: ah }),
					warn: L({}, e, { value: oh }),
					error: L({}, e, { value: sh }),
					group: L({}, e, { value: ch }),
					groupCollapsed: L({}, e, { value: lh }),
					groupEnd: L({}, e, { value: uh })
				});
			}
			0 > rh && console.error("disabledDepth fell below zero. This is a bug in React. Please file an issue.");
		}
		function Ae(e) {
			var t = Error.prepareStackTrace;
			if (Error.prepareStackTrace = void 0, e = e.stack, Error.prepareStackTrace = t, e.startsWith("Error: react-stack-top-frame\n") && (e = e.slice(29)), t = e.indexOf("\n"), t !== -1 && (e = e.slice(t + 1)), t = e.indexOf("react_stack_bottom_frame"), t !== -1 && (t = e.lastIndexOf("\n", t)), t !== -1) e = e.slice(0, t);
			else return "";
			return e;
		}
		function je(e) {
			if (dh === void 0) try {
				throw Error();
			} catch (e) {
				var t = e.stack.trim().match(/\n( *(at )?)/);
				dh = t && t[1] || "", fh = -1 < e.stack.indexOf("\n    at") ? " (<anonymous>)" : -1 < e.stack.indexOf("@") ? "@unknown:0:0" : "";
			}
			return "\n" + dh + e + fh;
		}
		function Me(e, t) {
			if (!e || ph) return "";
			var n = mh.get(e);
			if (n !== void 0) return n;
			ph = !0, n = Error.prepareStackTrace, Error.prepareStackTrace = void 0;
			var r = null;
			r = R.H, R.H = null, Oe();
			try {
				var i = { DetermineComponentFrameRoot: function() {
					try {
						if (t) {
							var n = function() {
								throw Error();
							};
							if (Object.defineProperty(n.prototype, "props", { set: function() {
								throw Error();
							} }), typeof Reflect == "object" && Reflect.construct) {
								try {
									Reflect.construct(n, []);
								} catch (e) {
									var r = e;
								}
								Reflect.construct(e, [], n);
							} else {
								try {
									n.call();
								} catch (e) {
									r = e;
								}
								n = !1;
								try {
									var i = Object.getOwnPropertyDescriptor(e.prototype, "props");
									Object.defineProperty(e.prototype, "props", {
										configurable: !0,
										set: function() {
											throw Error();
										}
									}), n = !0, new e();
								} finally {
									n && (i === void 0 ? delete e.prototype.props : Object.defineProperty(e.prototype, "props", i));
								}
							}
						} else {
							try {
								throw Error();
							} catch (e) {
								r = e;
							}
							(n = e()) && typeof n.catch == "function" && n.catch(function() {});
						}
					} catch (e) {
						if (e && r && typeof e.stack == "string") return [e.stack, r.stack];
					}
					return [null, null];
				} };
				i.DetermineComponentFrameRoot.displayName = "DetermineComponentFrameRoot";
				var a = Object.getOwnPropertyDescriptor(i.DetermineComponentFrameRoot, "name");
				a && a.configurable && Object.defineProperty(i.DetermineComponentFrameRoot, "name", { value: "DetermineComponentFrameRoot" });
				var o = i.DetermineComponentFrameRoot(), s = o[0], c = o[1];
				if (s && c) {
					var l = s.split("\n"), u = c.split("\n");
					for (o = a = 0; a < l.length && !l[a].includes("DetermineComponentFrameRoot");) a++;
					for (; o < u.length && !u[o].includes("DetermineComponentFrameRoot");) o++;
					if (a === l.length || o === u.length) for (a = l.length - 1, o = u.length - 1; 1 <= a && 0 <= o && l[a] !== u[o];) o--;
					for (; 1 <= a && 0 <= o; a--, o--) if (l[a] !== u[o]) {
						if (a !== 1 || o !== 1) do
							if (a--, o--, 0 > o || l[a] !== u[o]) {
								var d = "\n" + l[a].replace(" at new ", " at ");
								return e.displayName && d.includes("<anonymous>") && (d = d.replace("<anonymous>", e.displayName)), typeof e == "function" && mh.set(e, d), d;
							}
						while (1 <= a && 0 <= o);
						break;
					}
				}
			} finally {
				ph = !1, R.H = r, ke(), Error.prepareStackTrace = n;
			}
			return l = (l = e ? e.displayName || e.name : "") ? je(l) : "", typeof e == "function" && mh.set(e, l), l;
		}
		function Ne(e, t) {
			switch (e.tag) {
				case 26:
				case 27:
				case 5: return je(e.type);
				case 16: return je("Lazy");
				case 13: return e.child !== t && t !== null ? je("Suspense Fallback") : je("Suspense");
				case 19: return je("SuspenseList");
				case 0:
				case 15: return Me(e.type, !1);
				case 11: return Me(e.type.render, !1);
				case 1: return Me(e.type, !0);
				case 31: return je("Activity");
				case 30: return je("ViewTransition");
				default: return "";
			}
		}
		function Pe(e) {
			try {
				var t = "", n = null;
				do {
					t += Ne(e, n);
					var r = e._debugInfo;
					if (r) for (var i = r.length - 1; 0 <= i; i--) {
						var a = r[i];
						if (typeof a.name == "string") {
							var o = t;
							a: {
								var s = a.name, c = a.env, l = a.debugLocation;
								if (l != null) {
									var u = Ae(l), d = u.lastIndexOf("\n"), f = d === -1 ? u : u.slice(d + 1);
									if (f.indexOf(s) !== -1) {
										var p = "\n" + f;
										break a;
									}
								}
								p = je(s + (c ? " [" + c + "]" : ""));
							}
							t = o + p;
						}
					}
					n = e, e = e.return;
				} while (e);
				return t;
			} catch (e) {
				return "\nError generating stack: " + e.message + "\n" + e.stack;
			}
		}
		function Fe(e) {
			return (e = e ? e.displayName || e.name : "") ? je(e) : "";
		}
		function Ie() {
			if (hh === null) return null;
			var e = hh._debugOwner;
			return e == null ? null : ve(e);
		}
		function Le() {
			if (hh === null) return "";
			var e = hh;
			try {
				var t = "";
				switch (e.tag === 6 && (e = e.return), e.tag) {
					case 26:
					case 27:
					case 5:
						t += je(e.type);
						break;
					case 13:
						t += je("Suspense");
						break;
					case 19:
						t += je("SuspenseList");
						break;
					case 31:
						t += je("Activity");
						break;
					case 30:
						t += je("ViewTransition");
						break;
					case 0:
					case 15:
					case 1:
						e._debugOwner || t !== "" || (t += Fe(e.type));
						break;
					case 11: e._debugOwner || t !== "" || (t += Fe(e.type.render));
				}
				for (; e;) if (typeof e.tag == "number") {
					var n = e;
					e = n._debugOwner;
					var r = n._debugStack;
					if (e && r) {
						var i = Ae(r);
						i !== "" && (t += "\n" + i);
					}
				} else if (e.debugStack != null) {
					var a = e.debugStack;
					(e = e.owner) && a && (t += "\n" + Ae(a));
				} else break;
				var o = t;
			} catch (e) {
				o = "\nError generating stack: " + e.message + "\n" + e.stack;
			}
			return o;
		}
		function w(e, t, n, r, i, a, o) {
			var s = hh;
			Re(e);
			try {
				return e !== null && e._debugTask ? e._debugTask.run(t.bind(null, n, r, i, a, o)) : t(n, r, i, a, o);
			} finally {
				Re(s);
			}
			throw Error("runWithFiberInDEV should never be called in production. This is a bug in React.");
		}
		function Re(e) {
			R.getCurrentStack = e === null ? null : Le, gh = !1, hh = e;
		}
		function ze(e) {
			return typeof Symbol == "function" && Symbol.toStringTag && e[Symbol.toStringTag] || e.constructor.name || "Object";
		}
		function Be(e) {
			try {
				return Ve(e), !1;
			} catch {
				return !0;
			}
		}
		function Ve(e) {
			return "" + e;
		}
		function He(e, t) {
			if (Be(e)) return console.error("The provided `%s` attribute is an unsupported type %s. This value must be coerced to a string before using it here.", t, ze(e)), Ve(e);
		}
		function Ue(e, t) {
			if (Be(e)) return console.error("The provided `%s` CSS property is an unsupported type %s. This value must be coerced to a string before using it here.", t, ze(e)), Ve(e);
		}
		function We(e) {
			if (Be(e)) return console.error("Form field values (value, checked, defaultValue, or defaultChecked props) must be strings, not %s. This value must be coerced to a string before using it here.", ze(e)), Ve(e);
		}
		function Ge(e) {
			if (typeof __REACT_DEVTOOLS_GLOBAL_HOOK__ > "u") return !1;
			var t = __REACT_DEVTOOLS_GLOBAL_HOOK__;
			if (t.isDisabled) return !0;
			if (!t.supportsFiber) return console.error("The installed version of React DevTools is too old and will not work with the current version of React. Please update React DevTools. https://react.dev/link/react-devtools"), !0;
			try {
				jh = t.inject(e), Mh = t;
			} catch (e) {
				console.error("React instrumentation encountered an error: %o.", e);
			}
			return !!t.checkDCE;
		}
		function Ke(e) {
			if (typeof kh == "function" && Ah(e), Mh && typeof Mh.setStrictMode == "function") try {
				Mh.setStrictMode(jh, e);
			} catch (e) {
				Nh || (Nh = !0, console.error("React instrumentation encountered an error: %o", e));
			}
		}
		function qe(e) {
			return e >>>= 0, e === 0 ? 32 : 31 - (Ih(e) / Lh | 0) | 0;
		}
		function Je(e) {
			var t = e & 42;
			if (t !== 0) return t;
			switch (e & -e) {
				case 1: return 1;
				case 2: return 2;
				case 4: return 4;
				case 8: return 8;
				case 16: return 16;
				case 32: return 32;
				case 64: return 64;
				case 128: return 128;
				case 256:
				case 512:
				case 1024:
				case 2048:
				case 4096:
				case 8192:
				case 16384:
				case 32768:
				case 65536:
				case 131072: return e & -e;
				case 262144:
				case 524288:
				case 1048576:
				case 2097152: return e & 3932160;
				case 4194304:
				case 8388608:
				case 16777216:
				case 33554432: return e & 62914560;
				case 67108864: return 67108864;
				case 134217728: return 134217728;
				case 268435456: return 268435456;
				case 536870912: return 536870912;
				case 1073741824: return 0;
				default: return console.error("Should have found matching lanes. This is a bug in React."), e;
			}
		}
		function Ye(e, t, n) {
			var r = e.pendingLanes;
			if (r === 0) return 0;
			var i = 0, a = e.suspendedLanes, o = e.pingedLanes;
			e = e.warmLanes;
			var s = r & 134217727;
			return s === 0 ? (s = r & ~a, s === 0 ? o === 0 ? n || (n = r & ~e, n !== 0 && (i = Je(n))) : i = Je(o) : i = Je(s)) : (r = s & ~a, r === 0 ? (o &= s, o === 0 ? n || (n = s & ~e, n !== 0 && (i = Je(n))) : i = Je(o)) : i = Je(r)), i === 0 ? 0 : t !== 0 && t !== i && (t & a) === 0 && (a = i & -i, n = t & -t, a >= n || a === 32 && n & 4194048) ? t : i;
		}
		function Xe(e, t) {
			return (e.pendingLanes & ~(e.suspendedLanes & ~e.pingedLanes) & t) === 0;
		}
		function Ze(e, t) {
			t & 8 && (t |= t & 32);
			var n = e.entangledLanes;
			if (n !== 0) for (e = e.entanglements, n &= t; 0 < n;) {
				var r = 31 - Fh(n), i = 1 << r;
				t |= e[r], n &= ~i;
			}
			return t;
		}
		function Qe(e, t) {
			switch (e) {
				case 1:
				case 2:
				case 4:
				case 8:
				case 64: return t + 250;
				case 16:
				case 32:
				case 128:
				case 256:
				case 512:
				case 1024:
				case 2048:
				case 4096:
				case 8192:
				case 16384:
				case 32768:
				case 65536:
				case 131072:
				case 262144:
				case 524288:
				case 1048576:
				case 2097152: return t + 5e3;
				case 4194304:
				case 8388608:
				case 16777216:
				case 33554432: return -1;
				case 67108864:
				case 134217728:
				case 268435456:
				case 536870912:
				case 1073741824: return -1;
				default: return console.error("Should have found matching lanes. This is a bug in React."), -1;
			}
		}
		function $e() {
			var e = Bh;
			return Bh <<= 1, !(Bh & 62914560) && (Bh = 4194304), e;
		}
		function et(e) {
			for (var t = [], n = 0; 31 > n; n++) t.push(e);
			return t;
		}
		function tt(e, t) {
			e.pendingLanes |= t, t !== 268435456 && (e.suspendedLanes = 0, e.pingedLanes = 0, e.warmLanes = 0);
		}
		function nt(e, t, n, r, i, a) {
			var o = e.pendingLanes;
			e.pendingLanes = n, e.suspendedLanes = 0, e.pingedLanes = 0, e.warmLanes = 0, e.expiredLanes &= n, e.entangledLanes &= n, e.errorRecoveryDisabledLanes &= n, e.shellSuspendCounter = 0;
			var s = e.entanglements, c = e.expirationTimes, l = e.hiddenUpdates;
			for (n = o & ~n; 0 < n;) {
				var u = 31 - Fh(n), d = 1 << u;
				s[u] = 0, c[u] = -1;
				var f = l[u];
				if (f !== null) for (l[u] = null, u = 0; u < f.length; u++) {
					var p = f[u];
					p !== null && (p.lane &= -536870913);
				}
				n &= ~d;
			}
			r !== 0 && rt(e, r, 0), a !== 0 && i === 0 && e.tag !== 0 && (e.suspendedLanes |= a & ~(o & ~t));
		}
		function rt(e, t, n) {
			e.pendingLanes |= t, e.suspendedLanes &= ~t;
			var r = 31 - Fh(t);
			e.entangledLanes |= t, e.entanglements[r] = e.entanglements[r] | 1073741824 | n & 261930;
		}
		function it(e, t) {
			var n = e.entangledLanes |= t;
			for (e = e.entanglements; n;) {
				var r = 31 - Fh(n), i = 1 << r;
				i & t | e[r] & t && (e[r] |= t), n &= ~i;
			}
		}
		function at(e, t) {
			var n = t & -t;
			return n = n & 42 ? 1 : ot(n), (n & (e.suspendedLanes | t)) === 0 ? n : 0;
		}
		function ot(e) {
			switch (e) {
				case 2:
					e = 1;
					break;
				case 8:
					e = 4;
					break;
				case 32:
					e = 16;
					break;
				case 256:
				case 512:
				case 1024:
				case 2048:
				case 4096:
				case 8192:
				case 16384:
				case 32768:
				case 65536:
				case 131072:
				case 262144:
				case 524288:
				case 1048576:
				case 2097152:
				case 4194304:
				case 8388608:
				case 16777216:
				case 33554432:
					e = 128;
					break;
				case 268435456:
					e = 134217728;
					break;
				default: e = 0;
			}
			return e;
		}
		function st(e, t, n) {
			if (Ph) for (e = e.pendingUpdatersLaneMap; 0 < n;) {
				var r = 31 - Fh(n), i = 1 << r;
				e[r].add(t), n &= ~i;
			}
		}
		function ct(e, t) {
			if (Ph) for (var n = e.pendingUpdatersLaneMap, r = e.memoizedUpdaters; 0 < t;) {
				var i = 31 - Fh(t);
				e = 1 << i, i = n[i], 0 < i.size && (i.forEach(function(e) {
					var t = e.alternate;
					t !== null && r.has(t) || r.add(e);
				}), i.clear()), t &= ~e;
			}
		}
		function lt(e) {
			return e &= -e, Vh !== 0 && Vh < e ? Hh !== 0 && Hh < e ? e & 134217727 ? Uh : Wh : Hh : Vh;
		}
		function ut() {
			var e = z.p;
			return e === 0 ? (e = window.event, e === void 0 ? Uh : cm(e.type)) : e;
		}
		function dt(e, t) {
			var n = z.p;
			try {
				return z.p = e, t();
			} finally {
				z.p = n;
			}
		}
		function ft(e) {
			delete e[Kh], delete e[qh], delete e[Xh], delete e[Zh];
		}
		function pt(e) {
			var t;
			if (t = e[Kh]) return t;
			for (var n = e.parentNode; n;) {
				if (t = n[Jh] || n[Kh]) {
					if (n = t.alternate, t.child !== null || n !== null && n.child !== null) for (e = up(e); e !== null;) {
						if (n = e[Kh]) return n;
						e = up(e);
					}
					return t;
				}
				e = n, n = e.parentNode;
			}
			return null;
		}
		function mt(e) {
			if (e = e[Kh] || e[Jh]) {
				var t = e.tag;
				if (t === 5 || t === 6 || t === 13 || t === 31 || t === 26 || t === 27 || t === 3) return e;
			}
			return null;
		}
		function ht(e) {
			var t = e.tag;
			if (t === 5 || t === 26 || t === 27 || t === 6) return e.stateNode;
			throw Error("getNodeFromInstance: Invalid argument.");
		}
		function gt(e) {
			var t = e[Qh];
			return t ||= e[Qh] = {
				hoistableStyles: /* @__PURE__ */ new Map(),
				hoistableScripts: /* @__PURE__ */ new Map()
			}, t;
		}
		function _t(e) {
			e[$h] = !0;
		}
		function vt(e) {
			e[eg] = void 0;
		}
		function yt(e, t) {
			bt(e, t), bt(e + "Capture", t);
		}
		function bt(e, t) {
			ng[e] && console.error("EventRegistry: More than one plugin attempted to publish the same registration name, `%s`.", e), ng[e] = t;
			var n = e.toLowerCase();
			for (rg[n] = e, e === "onDoubleClick" && (rg.ondblclick = e), e = 0; e < t.length; e++) tg.add(t[e]);
		}
		function xt(e, t) {
			ig[t.type] || t.onChange || t.onInput || t.readOnly || t.disabled || t.value == null || console.error(e === "select" ? "You provided a `value` prop to a form field without an `onChange` handler. This will render a read-only field. If the field should be mutable use `defaultValue`. Otherwise, set `onChange`." : "You provided a `value` prop to a form field without an `onChange` handler. This will render a read-only field. If the field should be mutable use `defaultValue`. Otherwise, set either `onChange` or `readOnly`."), t.onChange || t.readOnly || t.disabled || t.checked == null || console.error("You provided a `checked` prop to a form field without an `onChange` handler. This will render a read-only field. If the field should be mutable use `defaultChecked`. Otherwise, set either `onChange` or `readOnly`.");
		}
		function St(e) {
			return _h.call(sg, e) ? !0 : _h.call(og, e) ? !1 : ag.test(e) ? sg[e] = !0 : (og[e] = !0, console.error("Invalid attribute name: `%s`", e), !1);
		}
		function Ct() {
			var e = B;
			return B = !1, e;
		}
		function wt(e, t, n) {
			if (St(t)) {
				if (!e.hasAttribute(t)) {
					switch (typeof n) {
						case "symbol":
						case "object": return n;
						case "function": return n;
						case "boolean": if (!1 === n) return n;
					}
					return n === void 0 ? void 0 : null;
				}
				return e = t.toLowerCase() === "nonce" ? e.nonce : e.getAttribute(t), e === "" && !0 === n || (He(n, t), e === "" + n ? n : e);
			}
		}
		function Tt(e, t, n) {
			if (St(t)) {
				if (n === null) e.removeAttribute(t);
				else {
					switch (typeof n) {
						case "undefined":
						case "function":
						case "symbol":
							e.removeAttribute(t);
							return;
						case "boolean":
							var r = t.toLowerCase().slice(0, 5);
							if (r !== "data-" && r !== "aria-") {
								e.removeAttribute(t);
								return;
							}
					}
					He(n, t), e.setAttribute(t, n);
				}
			}
		}
		function Et(e, t, n) {
			if (n === null) e.removeAttribute(t);
			else {
				switch (typeof n) {
					case "undefined":
					case "function":
					case "symbol":
					case "boolean":
						e.removeAttribute(t);
						return;
				}
				He(n, t), e.setAttribute(t, n);
			}
		}
		function Dt(e, t, n, r) {
			if (r === null) e.removeAttribute(n);
			else {
				switch (typeof r) {
					case "undefined":
					case "function":
					case "symbol":
					case "boolean":
						e.removeAttribute(n);
						return;
				}
				He(r, n), e.setAttributeNS(t, n, r);
			}
		}
		function Ot(e) {
			switch (typeof e) {
				case "bigint":
				case "boolean":
				case "number":
				case "string":
				case "undefined": return e;
				case "object": return We(e), e;
				default: return "";
			}
		}
		function kt(e) {
			var t = e.type;
			return (e = e.nodeName) && e.toLowerCase() === "input" && (t === "checkbox" || t === "radio");
		}
		function At(e, t, n) {
			var r = Object.getOwnPropertyDescriptor(e.constructor.prototype, t);
			if (!e.hasOwnProperty(t) && r !== void 0 && typeof r.get == "function" && typeof r.set == "function") {
				var i = r.get, a = r.set;
				return Object.defineProperty(e, t, {
					configurable: !0,
					get: function() {
						return i.call(this);
					},
					set: function(e) {
						We(e), n = "" + e, a.call(this, e);
					}
				}), Object.defineProperty(e, t, { enumerable: r.enumerable }), {
					getValue: function() {
						return n;
					},
					setValue: function(e) {
						We(e), n = "" + e;
					},
					stopTracking: function() {
						e._valueTracker = null, delete e[t];
					}
				};
			}
		}
		function jt(e) {
			if (!e._valueTracker) {
				var t = kt(e) ? "checked" : "value";
				e._valueTracker = At(e, t, "" + e[t]);
			}
		}
		function Mt(e) {
			if (!e) return !1;
			var t = e._valueTracker;
			if (!t) return !0;
			var n = t.getValue(), r = "";
			return e && (r = kt(e) ? e.checked ? "true" : "false" : e.value), e = r, e !== n && (t.setValue(e), !0);
		}
		function Nt(e) {
			return e.replace(cg, function(e) {
				return "\\" + e.charCodeAt(0).toString(16) + " ";
			});
		}
		function Pt(e, t) {
			t.checked === void 0 || t.defaultChecked === void 0 || ug || (console.error("%s contains an input of type %s with both checked and defaultChecked props. Input elements must be either controlled or uncontrolled (specify either the checked prop, or the defaultChecked prop, but not both). Decide between using a controlled or uncontrolled input element and remove one of these props. More info: https://react.dev/link/controlled-components", Ie() || "A component", t.type), ug = !0), t.value === void 0 || t.defaultValue === void 0 || lg || (console.error("%s contains an input of type %s with both value and defaultValue props. Input elements must be either controlled or uncontrolled (specify either the value prop, or the defaultValue prop, but not both). Decide between using a controlled or uncontrolled input element and remove one of these props. More info: https://react.dev/link/controlled-components", Ie() || "A component", t.type), lg = !0);
		}
		function Ft(e, t, n, r, i, a, o, s) {
			e.name = "", o != null && typeof o != "function" && typeof o != "symbol" && typeof o != "boolean" ? (He(o, "type"), e.type = o) : e.removeAttribute("type"), t == null ? o !== "submit" && o !== "reset" || e.removeAttribute("value") : o === "number" ? (t === 0 && e.value === "" || e.value != t) && (e.value = "" + Ot(t)) : e.value !== "" + Ot(t) && (e.value = "" + Ot(t)), t == null ? n == null ? r != null && e.removeAttribute("value") : Lt(e, Ot(n)) : o === "number" && e.value == t ? Lt(e, Ot(e.value)) : Lt(e, Ot(t)), i == null && a != null && (e.defaultChecked = !!a), i != null && (e.checked = i && typeof i != "function" && typeof i != "symbol"), s != null && typeof s != "function" && typeof s != "symbol" && typeof s != "boolean" ? (He(s, "name"), e.name = "" + Ot(s)) : e.removeAttribute("name");
		}
		function It(e, t, n, r, i, a, o, s) {
			if (a != null && typeof a != "function" && typeof a != "symbol" && typeof a != "boolean" && (He(a, "type"), e.type = a), t != null || n != null) {
				if (!(a !== "submit" && a !== "reset" || t != null)) {
					jt(e);
					return;
				}
				n = n == null ? "" : "" + Ot(n), t = t == null ? n : "" + Ot(t), s || t === e.value || (e.value = t), e.defaultValue = t;
			}
			r ??= i, r = typeof r != "function" && typeof r != "symbol" && !!r, e.checked = s ? e.checked : !!r, e.defaultChecked = !!r, o != null && typeof o != "function" && typeof o != "symbol" && typeof o != "boolean" && (He(o, "name"), e.name = o), jt(e);
		}
		function Lt(e, t) {
			e.defaultValue !== "" + t && (e.defaultValue = "" + t);
		}
		function Rt(e, t) {
			t.value ?? (typeof t.children == "object" && t.children !== null ? wm.Children.forEach(t.children, function(e) {
				e == null || typeof e == "string" || typeof e == "number" || typeof e == "bigint" || fg || (fg = !0, console.error("Cannot infer the option value of complex children. Pass a `value` prop or use a plain string as children to <option>."));
			}) : t.dangerouslySetInnerHTML == null || pg || (pg = !0, console.error("Pass a `value` prop if you set dangerouslyInnerHTML so React knows which value should be selected."))), t.selected == null || dg || (console.error("Use the `defaultValue` or `value` props on <select> instead of setting `selected` on <option>."), dg = !0);
		}
		function zt() {
			var e = Ie();
			return e ? "\n\nCheck the render method of `" + e + "`." : "";
		}
		function Bt(e, t, n, r) {
			if (e = e.options, t) {
				t = {};
				for (var i = 0; i < n.length; i++) t["$" + n[i]] = !0;
				for (n = 0; n < e.length; n++) i = t.hasOwnProperty("$" + e[n].value), e[n].selected !== i && (e[n].selected = i), i && r && (e[n].defaultSelected = !0);
			} else {
				for (n = "" + Ot(n), t = null, i = 0; i < e.length; i++) {
					if (e[i].value === n) {
						e[i].selected = !0, r && (e[i].defaultSelected = !0);
						return;
					}
					t !== null || e[i].disabled || (t = e[i]);
				}
				t !== null && (t.selected = !0);
			}
		}
		function Vt(e, t) {
			for (e = 0; e < hg.length; e++) {
				var n = hg[e];
				if (t[n] != null) {
					var r = Jm(t[n]);
					t.multiple && !r ? console.error("The `%s` prop supplied to <select> must be an array if `multiple` is true.%s", n, zt()) : !t.multiple && r && console.error("The `%s` prop supplied to <select> must be a scalar value if `multiple` is false.%s", n, zt());
				}
			}
			t.value === void 0 || t.defaultValue === void 0 || mg || (console.error("Select elements must be either controlled or uncontrolled (specify either the value prop, or the defaultValue prop, but not both). Decide between using a controlled or uncontrolled select element and remove one of these props. More info: https://react.dev/link/controlled-components"), mg = !0);
		}
		function Ht(e, t) {
			t.value === void 0 || t.defaultValue === void 0 || gg || (console.error("%s contains a textarea with both value and defaultValue props. Textarea elements must be either controlled or uncontrolled (specify either the value prop, or the defaultValue prop, but not both). Decide between using a controlled or uncontrolled textarea and remove one of these props. More info: https://react.dev/link/controlled-components", Ie() || "A component"), gg = !0), t.children != null && t.value == null && console.error("Use the `defaultValue` or `value` props instead of setting children on <textarea>.");
		}
		function Ut(e, t, n) {
			t != null && (t = "" + Ot(t), t !== e.value && (e.value = t), n == null) ? e.defaultValue !== t && (e.defaultValue = t) : e.defaultValue = n == null ? "" : "" + Ot(n);
		}
		function Wt(e, t, n, r) {
			if (t == null) {
				if (r != null) {
					if (n != null) throw Error("If you supply `defaultValue` on a <textarea>, do not pass children.");
					if (Jm(r)) {
						if (1 < r.length) throw Error("<textarea> can only have at most one child.");
						r = r[0];
					}
					n = r;
				}
				n ??= "", t = n;
			}
			n = Ot(t), e.defaultValue = n, r = e.textContent, r === n && r !== "" && r !== null && (e.value = r), jt(e);
		}
		function Gt(e, t) {
			return e.serverProps === void 0 && e.serverTail.length === 0 && e.children.length === 1 && 3 < e.distanceFromLeaf && e.distanceFromLeaf > 15 - t ? Gt(e.children[0], t) : e;
		}
		function Kt(e) {
			return "  " + "  ".repeat(e);
		}
		function qt(e) {
			return "+ " + "  ".repeat(e);
		}
		function Jt(e) {
			return "- " + "  ".repeat(e);
		}
		function Yt(e) {
			switch (e.tag) {
				case 26:
				case 27:
				case 5: return e.type;
				case 16: return "Lazy";
				case 31: return "Activity";
				case 13: return "Suspense";
				case 19: return "SuspenseList";
				case 0:
				case 15: return e = e.type, e.displayName || e.name || null;
				case 11: return e = e.type.render, e.displayName || e.name || null;
				case 1: return e = e.type, e.displayName || e.name || null;
				default: return null;
			}
		}
		function Xt(e, t) {
			return _g.test(e) ? (e = JSON.stringify(e), e.length > t - 2 ? 8 > t ? "{\"...\"}" : "{" + e.slice(0, t - 7) + "...\"}" : "{" + e + "}") : e.length > t ? 5 > t ? "{\"...\"}" : e.slice(0, t - 3) + "..." : e;
		}
		function Zt(e, t, n) {
			var r = 120 - 2 * n;
			if (t === null) return qt(n) + Xt(e, r) + "\n";
			if (typeof t == "string") {
				for (var i = 0; i < t.length && i < e.length && t.charCodeAt(i) === e.charCodeAt(i); i++);
				return i > r - 8 && 10 < i && (e = "..." + e.slice(i - 8), t = "..." + t.slice(i - 8)), qt(n) + Xt(e, r) + "\n" + Jt(n) + Xt(t, r) + "\n";
			}
			return Kt(n) + Xt(e, r) + "\n";
		}
		function Qt(e) {
			return Object.prototype.toString.call(e).replace(/^\[object (.*)\]$/, function(e, t) {
				return t;
			});
		}
		function $t(e, t) {
			switch (typeof e) {
				case "string": return e = JSON.stringify(e), e.length > t ? 5 > t ? "\"...\"" : e.slice(0, t - 4) + "...\"" : e;
				case "object":
					if (e === null) return "null";
					if (Jm(e)) return "[...]";
					if (e.$$typeof === km) return (t = _e(e.type)) ? "<" + t + ">" : "<...>";
					var n = Qt(e);
					if (n === "Object") {
						for (var r in n = "", t -= 2, e) if (e.hasOwnProperty(r)) {
							var i = JSON.stringify(r);
							if (i !== "\"" + r + "\"" && (r = i), t -= r.length - 2, i = $t(e[r], 15 > t ? t : 15), t -= i.length, 0 > t) {
								n += n === "" ? "..." : ", ...";
								break;
							}
							n += (n === "" ? "" : ",") + r + ":" + i;
						}
						return "{" + n + "}";
					}
					return n;
				case "function": return (t = e.displayName || e.name) ? "function " + t : "function";
				default: return String(e);
			}
		}
		function en(e, t) {
			return typeof e != "string" || _g.test(e) ? "{" + $t(e, t - 2) + "}" : e.length > t - 2 ? 5 > t ? "\"...\"" : "\"" + e.slice(0, t - 5) + "...\"" : "\"" + e + "\"";
		}
		function tn(e, t, n) {
			var r = 120 - n.length - e.length, i = [], a;
			for (a in t) if (t.hasOwnProperty(a) && a !== "children") {
				var o = en(t[a], 120 - n.length - a.length - 1);
				r -= a.length + o.length + 2, i.push(a + "=" + o);
			}
			return i.length === 0 ? n + "<" + e + ">\n" : 0 < r ? n + "<" + e + " " + i.join(" ") + ">\n" : n + "<" + e + "\n" + n + "  " + i.join("\n" + n + "  ") + "\n" + n + ">\n";
		}
		function nn(e, t, n) {
			var r = "", i = L({}, t), a;
			for (a in e) if (e.hasOwnProperty(a)) {
				delete i[a];
				var o = 120 - 2 * n - a.length - 2, s = $t(e[a], o);
				t.hasOwnProperty(a) ? (o = $t(t[a], o), r += qt(n) + a + ": " + s + "\n", r += Jt(n) + a + ": " + o + "\n") : r += qt(n) + a + ": " + s + "\n";
			}
			for (var c in i) i.hasOwnProperty(c) && (e = $t(i[c], 120 - 2 * n - c.length - 2), r += Jt(n) + c + ": " + e + "\n");
			return r;
		}
		function rn(e, t, n, r) {
			var i = "", a = /* @__PURE__ */ new Map();
			for (l in n) n.hasOwnProperty(l) && a.set(l.toLowerCase(), l);
			if (a.size === 1 && a.has("children")) i += tn(e, t, Kt(r));
			else {
				for (var o in t) if (t.hasOwnProperty(o) && o !== "children") {
					var s = 120 - 2 * (r + 1) - o.length - 1, c = a.get(o.toLowerCase());
					if (c !== void 0) {
						a.delete(o.toLowerCase());
						var l = t[o];
						c = n[c];
						var u = en(l, s);
						s = en(c, s), typeof l == "object" && l && typeof c == "object" && c && Qt(l) === "Object" && Qt(c) === "Object" && (2 < Object.keys(l).length || 2 < Object.keys(c).length || -1 < u.indexOf("...") || -1 < s.indexOf("...")) ? i += Kt(r + 1) + o + "={{\n" + nn(l, c, r + 2) + Kt(r + 1) + "}}\n" : (i += qt(r + 1) + o + "=" + u + "\n", i += Jt(r + 1) + o + "=" + s + "\n");
					} else i += Kt(r + 1) + o + "=" + en(t[o], s) + "\n";
				}
				a.forEach(function(e) {
					if (e !== "children") {
						var t = 120 - 2 * (r + 1) - e.length - 1;
						i += Jt(r + 1) + e + "=" + en(n[e], t) + "\n";
					}
				}), i = i === "" ? Kt(r) + "<" + e + ">\n" : Kt(r) + "<" + e + "\n" + i + Kt(r) + ">\n";
			}
			return e = n.children, t = t.children, typeof e == "string" || typeof e == "number" || typeof e == "bigint" ? (a = "", (typeof t == "string" || typeof t == "number" || typeof t == "bigint") && (a = "" + t), i += Zt(a, "" + e, r + 1)) : (typeof t == "string" || typeof t == "number" || typeof t == "bigint") && (i = e == null ? i + Zt("" + t, null, r + 1) : i + Zt("" + t, void 0, r + 1)), i;
		}
		function an(e, t) {
			var n = Yt(e);
			if (n === null) {
				for (n = "", e = e.child; e;) n += an(e, t), e = e.sibling;
				return n;
			}
			return Kt(t) + "<" + n + ">\n";
		}
		function on(e, t) {
			var n = Gt(e, t);
			if (n !== e && (e.children.length !== 1 || e.children[0] !== n)) return Kt(t) + "...\n" + on(n, t + 1);
			n = "";
			var r = e.fiber._debugInfo;
			if (r) for (var i = 0; i < r.length; i++) {
				var a = r[i].name;
				typeof a == "string" && (n += Kt(t) + "<" + a + ">\n", t++);
			}
			if (r = "", i = e.fiber.pendingProps, e.fiber.tag === 6) r = Zt(i, e.serverProps, t), t++;
			else if (a = Yt(e.fiber), a !== null) {
				if (e.serverProps === void 0) {
					r = t;
					var o = 120 - 2 * r - a.length - 2, s = "";
					for (l in i) if (i.hasOwnProperty(l) && l !== "children") {
						var c = en(i[l], 15);
						if (o -= l.length + c.length + 2, 0 > o) {
							s += " ...";
							break;
						}
						s += " " + l + "=" + c;
					}
					r = Kt(r) + "<" + a + s + ">\n", t++;
				} else e.serverProps === null ? (r = tn(a, i, qt(t)), t++) : typeof e.serverProps == "string" ? console.error("Should not have matched a non HostText fiber to a Text node. This is a bug in React.") : (r = rn(a, i, e.serverProps, t), t++);
			}
			var l = "";
			for (i = e.fiber.child, a = 0; i && a < e.children.length;) o = e.children[a], o.fiber === i ? (l += on(o, t), a++) : l += an(i, t), i = i.sibling;
			for (i && 0 < e.children.length && (l += Kt(t) + "...\n"), i = e.serverTail, e.serverProps === null && t--, e = 0; e < i.length; e++) a = i[e], l = typeof a == "string" ? l + (Jt(t) + Xt(a, 120 - 2 * t) + "\n") : l + tn(a.type, a.props, Jt(t));
			return n + r + l;
		}
		function T(e) {
			try {
				return "\n\n" + on(e, 0);
			} catch {
				return "";
			}
		}
		function sn(e, t, n) {
			for (var r = t, i = null, a = 0; r;) r === e && (a = 0), i = {
				fiber: r,
				children: i === null ? [] : [i],
				serverProps: r === t ? n : r === e ? null : void 0,
				serverTail: [],
				distanceFromLeaf: a
			}, a++, r = r.return;
			return i === null ? "" : T(i).replaceAll(/^[+-]/gm, ">");
		}
		function cn(e, t) {
			var n = L({}, e || Sg), r = { tag: t };
			return yg.indexOf(t) !== -1 && (n.aTagInScope = null, n.buttonTagInScope = null, n.nobrTagInScope = null), bg.indexOf(t) !== -1 && (n.pTagInButtonScope = null), vg.indexOf(t) !== -1 && t !== "address" && t !== "div" && t !== "p" && (n.listItemTagAutoclosing = null, n.dlItemTagAutoclosing = null), n.current = r, t === "form" && (n.formTag = r), t === "a" && (n.aTagInScope = r), t === "button" && (n.buttonTagInScope = r), t === "nobr" && (n.nobrTagInScope = r), t === "p" && (n.pTagInButtonScope = r), t === "li" && (n.listItemTagAutoclosing = r), (t === "dd" || t === "dt") && (n.dlItemTagAutoclosing = r), t === "#document" || t === "html" ? n.containerTagInScope = null : n.containerTagInScope ||= r, e !== null || t !== "#document" && t !== "html" && t !== "body" ? !0 === n.implicitRootScope && (n.implicitRootScope = !1) : n.implicitRootScope = !0, n;
		}
		function ln(e, t, n) {
			switch (t) {
				case "tr": return e === "th" || e === "td" || e === "style" || e === "script" || e === "template";
				case "tbody":
				case "thead":
				case "tfoot": return e === "tr" || e === "style" || e === "script" || e === "template";
				case "colgroup": return e === "col" || e === "template";
				case "table": return e === "caption" || e === "colgroup" || e === "tbody" || e === "tfoot" || e === "thead" || e === "style" || e === "script" || e === "template";
				case "head": return e === "base" || e === "basefont" || e === "bgsound" || e === "link" || e === "meta" || e === "title" || e === "noscript" || e === "noframes" || e === "style" || e === "script" || e === "template";
				case "html":
					if (n) break;
					return e === "head" || e === "body" || e === "frameset";
				case "frameset": return e === "frame";
				case "#document": if (!n) return e === "html";
			}
			switch (e) {
				case "h1":
				case "h2":
				case "h3":
				case "h4":
				case "h5":
				case "h6": return t !== "h1" && t !== "h2" && t !== "h3" && t !== "h4" && t !== "h5" && t !== "h6";
				case "rp":
				case "rt": return xg.indexOf(t) === -1;
				case "caption":
				case "col":
				case "colgroup":
				case "input": return t !== "select";
				case "frameset":
				case "frame":
				case "tbody":
				case "td":
				case "tfoot":
				case "th":
				case "thead":
				case "tr": return t == null;
				case "head": return n || t === null;
				case "html": return n && t === "#document" || t === null;
				case "body": return n && (t === "#document" || t === "html") || t === null;
			}
			return !0;
		}
		function un(e, t) {
			switch (e) {
				case "address":
				case "article":
				case "aside":
				case "blockquote":
				case "center":
				case "details":
				case "dialog":
				case "dir":
				case "div":
				case "dl":
				case "fieldset":
				case "figcaption":
				case "figure":
				case "footer":
				case "header":
				case "hgroup":
				case "main":
				case "menu":
				case "nav":
				case "ol":
				case "p":
				case "section":
				case "summary":
				case "ul":
				case "pre":
				case "listing":
				case "table":
				case "hr":
				case "xmp":
				case "h1":
				case "h2":
				case "h3":
				case "h4":
				case "h5":
				case "h6": return t.pTagInButtonScope;
				case "form": return t.formTag || t.pTagInButtonScope;
				case "li": return t.listItemTagAutoclosing;
				case "dd":
				case "dt": return t.dlItemTagAutoclosing;
				case "button": return t.buttonTagInScope;
				case "a": return t.aTagInScope;
				case "nobr": return t.nobrTagInScope;
			}
			return null;
		}
		function dn(e, t) {
			for (; e;) {
				switch (e.tag) {
					case 5:
					case 26:
					case 27: if (e.type === t) return e;
				}
				e = e.return;
			}
			return null;
		}
		function fn(e, t) {
			t ||= Sg;
			var n = t.current;
			if (t = (n = ln(e, n && n.tag, t.implicitRootScope) ? null : n) ? null : un(e, t), t = n || t, !t) return !0;
			var r = t.tag;
			if (t = String(!!n) + "|" + e + "|" + r, Cg[t]) return !1;
			Cg[t] = !0;
			var i = (t = hh) ? dn(t.return, r) : null, a = t !== null && i !== null ? sn(i, t, null) : "", o = "<" + e + ">";
			return n ? (n = "", r === "table" && e === "tr" && (n += " Add a <tbody>, <thead> or <tfoot> to your code to match the DOM tree generated by the browser."), console.error("In HTML, %s cannot be a child of <%s>.%s\nThis will cause a hydration error.%s", o, r, n, a)) : console.error("In HTML, %s cannot be a descendant of <%s>.\nThis will cause a hydration error.%s", o, r, a), t && (e = t.return, i === null || e === null || i === e && e._debugOwner === t._debugOwner || w(i, function() {
				console.error("<%s> cannot contain a nested %s.\nSee this log for the ancestor stack trace.", r, o);
			})), !1;
		}
		function pn(e, t, n) {
			if (n || ln("#text", t, !1)) return !0;
			if (n = "#text|" + t, Cg[n]) return !1;
			Cg[n] = !0;
			var r = (n = hh) ? dn(n, t) : null;
			return n = n !== null && r !== null ? sn(r, n, n.tag === 6 ? null : { children: null }) : "", /\S/.test(e) ? console.error("In HTML, text nodes cannot be a child of <%s>.\nThis will cause a hydration error.%s", t, n) : console.error("In HTML, whitespace text nodes cannot be a child of <%s>. Make sure you don't have any extra whitespace between tags on each line of your source code.\nThis will cause a hydration error.%s", t, n), !1;
		}
		function mn(e, t) {
			if (t) {
				var n = e.firstChild;
				if (n && n === e.lastChild && n.nodeType === 3) {
					n.nodeValue = t;
					return;
				}
			}
			e.textContent = t;
		}
		function hn(e) {
			return e.replace(kg, function(e, t) {
				return t.toUpperCase();
			});
		}
		function gn(e, t, n) {
			var r = t.indexOf("--") === 0;
			r || (-1 < t.indexOf("-") ? jg.hasOwnProperty(t) && jg[t] || (jg[t] = !0, console.error("Unsupported style property %s. Did you mean %s?", t, hn(t.replace(Og, "ms-")))) : Dg.test(t) ? jg.hasOwnProperty(t) && jg[t] || (jg[t] = !0, console.error("Unsupported vendor-prefixed style property %s. Did you mean %s?", t, t.charAt(0).toUpperCase() + t.slice(1))) : !Ag.test(n) || Mg.hasOwnProperty(n) && Mg[n] || (Mg[n] = !0, console.error("Style property values shouldn't contain a semicolon. Try \"%s: %s\" instead.", t, n.replace(Ag, ""))), typeof n == "number" && (isNaN(n) ? Ng || (Ng = !0, console.error("`NaN` is an invalid value for the `%s` css style property.", t)) : isFinite(n) || Pg || (Pg = !0, console.error("`Infinity` is an invalid value for the `%s` css style property.", t)))), n == null || typeof n == "boolean" || n === "" ? r ? e.setProperty(t, "") : t === "float" ? e.cssFloat = "" : e[t] = "" : r ? e.setProperty(t, n) : typeof n != "number" || n === 0 || Fg.has(t) ? t === "float" ? e.cssFloat = n : (Ue(n, t), e[t] = ("" + n).trim()) : e[t] = n + "px";
		}
		function _n(e, t, n) {
			if (t != null && typeof t != "object") throw Error("The `style` prop expects a mapping from style properties to values, not a string. For example, style={{marginRight: spacing + 'em'}} when using JSX.");
			if (t && Object.freeze(t), e = e.style, n != null) {
				if (t) {
					var r = {};
					if (n) {
						for (var i in n) if (n.hasOwnProperty(i) && !t.hasOwnProperty(i)) for (var a = wg[i] || [i], o = 0; o < a.length; o++) r[a[o]] = i;
					}
					for (var s in t) if (t.hasOwnProperty(s) && (!n || n[s] !== t[s])) for (i = wg[s] || [s], a = 0; a < i.length; a++) r[i[a]] = s;
					for (var c in s = {}, t) for (i = wg[c] || [c], a = 0; a < i.length; a++) s[i[a]] = c;
					for (var l in c = {}, r) if (i = r[l], (a = s[l]) && i !== a && (o = i + "," + a, !c[o])) {
						c[o] = !0, o = console;
						var u = t[i];
						o.error.call(o, "%s a style property during rerender (%s) when a conflicting property is set (%s) can lead to styling bugs. To avoid this, don't mix shorthand and non-shorthand properties for the same value; instead, replace the shorthand with separate values.", u == null || typeof u == "boolean" || u === "" ? "Removing" : "Updating", i, a);
					}
				}
				for (var d in n) !n.hasOwnProperty(d) || t != null && t.hasOwnProperty(d) || (d.indexOf("--") === 0 ? e.setProperty(d, "") : d === "float" ? e.cssFloat = "" : e[d] = "", B = !0);
				for (var f in t) l = t[f], t.hasOwnProperty(f) && n[f] !== l && (gn(e, f, l), B = !0);
			} else for (r in t) t.hasOwnProperty(r) && gn(e, r, t[r]);
		}
		function vn(e) {
			if (e.indexOf("-") === -1) return !1;
			switch (e) {
				case "annotation-xml":
				case "color-profile":
				case "font-face":
				case "font-face-src":
				case "font-face-uri":
				case "font-face-format":
				case "font-face-name":
				case "missing-glyph": return !1;
				default: return !0;
			}
		}
		function yn(e) {
			return Rg.get(e) || e;
		}
		function bn(e, t) {
			if (_h.call(Vg, t) && Vg[t]) return !0;
			if (Ug.test(t)) {
				if (e = "aria-" + t.slice(4).toLowerCase(), e = Bg.hasOwnProperty(e) ? e : null, e == null) return console.error("Invalid ARIA attribute `%s`. ARIA attributes follow the pattern aria-* and must be lowercase.", t), Vg[t] = !0;
				if (t !== e) return console.error("Invalid ARIA attribute `%s`. Did you mean `%s`?", t, e), Vg[t] = !0;
			}
			if (Hg.test(t)) {
				if (e = t.toLowerCase(), e = Bg.hasOwnProperty(e) ? e : null, e == null) return Vg[t] = !0, !1;
				t !== e && (console.error("Unknown ARIA attribute `%s`. Did you mean `%s`?", t, e), Vg[t] = !0);
			}
			return !0;
		}
		function xn(e, t) {
			var n = [], r;
			for (r in t) bn(e, r) || n.push(r);
			t = n.map(function(e) {
				return "`" + e + "`";
			}).join(", "), n.length === 1 ? console.error("Invalid aria prop %s on <%s> tag. For details, see https://react.dev/link/invalid-aria-props", t, e) : 1 < n.length && console.error("Invalid aria props %s on <%s> tag. For details, see https://react.dev/link/invalid-aria-props", t, e);
		}
		function Sn(e, t, n, r) {
			if (_h.call(Gg, t) && Gg[t]) return !0;
			var i = t.toLowerCase();
			if (i === "onfocusin" || i === "onfocusout") return console.error("React uses onFocus and onBlur instead of onFocusIn and onFocusOut. All React events are normalized to bubble, so onFocusIn and onFocusOut are not needed/supported by React."), Gg[t] = !0;
			if (typeof n == "function" && (e === "form" && t === "action" || e === "input" && t === "formAction" || e === "button" && t === "formAction")) return !0;
			if (r != null) {
				if (e = r.possibleRegistrationNames, r.registrationNameDependencies.hasOwnProperty(t)) return !0;
				if (r = e.hasOwnProperty(i) ? e[i] : null, r != null) return console.error("Invalid event handler property `%s`. Did you mean `%s`?", t, r), Gg[t] = !0;
				if (Kg.test(t)) return console.error("Unknown event handler property `%s`. It will be ignored.", t), Gg[t] = !0;
			} else if (Kg.test(t)) return qg.test(t) && console.error("Invalid event handler property `%s`. React events use the camelCase naming convention, for example `onClick`.", t), Gg[t] = !0;
			if (Jg.test(t) || Yg.test(t)) return !0;
			if (i === "innerhtml") return console.error("Directly setting property `innerHTML` is not permitted. For more information, lookup documentation on `dangerouslySetInnerHTML`."), Gg[t] = !0;
			if (i === "aria") return console.error("The `aria` attribute is reserved for future use in React. Pass individual `aria-` attributes instead."), Gg[t] = !0;
			if (i === "is" && n != null && typeof n != "string") return console.error("Received a `%s` for a string attribute `is`. If this is expected, cast the value to a string.", typeof n), Gg[t] = !0;
			if (typeof n == "number" && isNaN(n)) return console.error("Received NaN for the `%s` attribute. If this is expected, cast the value to a string.", t), Gg[t] = !0;
			if (zg.hasOwnProperty(i)) {
				if (i = zg[i], i !== t) return console.error("Invalid DOM property `%s`. Did you mean `%s`?", t, i), Gg[t] = !0;
			} else if (t !== i) return console.error("React does not recognize the `%s` prop on a DOM element. If you intentionally want it to appear in the DOM as a custom attribute, spell it as lowercase `%s` instead. If you accidentally passed it from a parent component, remove it from the DOM element.", t, i), Gg[t] = !0;
			switch (t) {
				case "dangerouslySetInnerHTML":
				case "children":
				case "style":
				case "suppressContentEditableWarning":
				case "suppressHydrationWarning":
				case "defaultValue":
				case "defaultChecked":
				case "innerHTML":
				case "ref": return !0;
				case "innerText":
				case "textContent": return !0;
			}
			switch (typeof n) {
				case "boolean": switch (t) {
					case "autoFocus":
					case "checked":
					case "multiple":
					case "muted":
					case "selected":
					case "contentEditable":
					case "spellCheck":
					case "draggable":
					case "value":
					case "autoReverse":
					case "externalResourcesRequired":
					case "focusable":
					case "preserveAlpha":
					case "allowFullScreen":
					case "async":
					case "autoPlay":
					case "controls":
					case "credentialless":
					case "default":
					case "defer":
					case "disabled":
					case "disablePictureInPicture":
					case "disableRemotePlayback":
					case "formNoValidate":
					case "hidden":
					case "loop":
					case "noModule":
					case "noValidate":
					case "open":
					case "playsInline":
					case "readOnly":
					case "required":
					case "reversed":
					case "scoped":
					case "seamless":
					case "itemScope":
					case "capture":
					case "download":
					case "inert": return !0;
					default: return i = t.toLowerCase().slice(0, 5), i === "data-" || i === "aria-" || (n ? console.error("Received `%s` for a non-boolean attribute `%s`.\n\nIf you want to write it to the DOM, pass a string instead: %s=\"%s\" or %s={value.toString()}.", n, t, t, n, t) : console.error("Received `%s` for a non-boolean attribute `%s`.\n\nIf you want to write it to the DOM, pass a string instead: %s=\"%s\" or %s={value.toString()}.\n\nIf you used to conditionally omit it with %s={condition && value}, pass %s={condition ? value : undefined} instead.", n, t, t, n, t, t, t), Gg[t] = !0);
				}
				case "function":
				case "symbol": return Gg[t] = !0, !1;
				case "string": if (n === "false" || n === "true") {
					switch (t) {
						case "checked":
						case "selected":
						case "multiple":
						case "muted":
						case "allowFullScreen":
						case "async":
						case "autoPlay":
						case "controls":
						case "credentialless":
						case "default":
						case "defer":
						case "disabled":
						case "disablePictureInPicture":
						case "disableRemotePlayback":
						case "formNoValidate":
						case "hidden":
						case "loop":
						case "noModule":
						case "noValidate":
						case "open":
						case "playsInline":
						case "readOnly":
						case "required":
						case "reversed":
						case "scoped":
						case "seamless":
						case "itemScope":
						case "inert": break;
						default: return !0;
					}
					console.error("Received the string `%s` for the boolean attribute `%s`. %s Did you mean %s={%s}?", n, t, n === "false" ? "The browser will interpret it as a truthy value." : "Although this works, it will not work as expected if you pass the string \"false\".", t, n), Gg[t] = !0;
				}
			}
			return !0;
		}
		function Cn(e, t, n) {
			var r = [], i;
			for (i in t) Sn(e, i, t[i], n) || r.push(i);
			t = r.map(function(e) {
				return "`" + e + "`";
			}).join(", "), r.length === 1 ? console.error("Invalid value for prop %s on <%s> tag. Either remove it from the element, or pass a string or number value to keep it in the DOM. For details, see https://react.dev/link/attribute-behavior ", t, e) : 1 < r.length && console.error("Invalid values for props %s on <%s> tag. Either remove them from the element, or pass a string or number value to keep them in the DOM. For details, see https://react.dev/link/attribute-behavior ", t, e);
		}
		function wn(e) {
			return Xg.test("" + e) ? "javascript:throw new Error('React has blocked a javascript: URL as a security precaution.')" : e;
		}
		function Tn() {}
		function En(e) {
			return e = e.target || e.srcElement || window, e.correspondingUseElement && (e = e.correspondingUseElement), e.nodeType === 3 ? e.parentNode : e;
		}
		function Dn(e) {
			var t = mt(e);
			if (t && (e = t.stateNode)) {
				var n = e[qh] || null;
				a: switch (e = t.stateNode, t.type) {
					case "input":
						if (Ft(e, n.value, n.defaultValue, n.defaultValue, n.checked, n.defaultChecked, n.type, n.name), t = n.name, n.type === "radio" && t != null) {
							for (n = e; n.parentNode;) n = n.parentNode;
							for (He(t, "name"), n = n.querySelectorAll("input[name=\"" + Nt("" + t) + "\"][type=\"radio\"]"), t = 0; t < n.length; t++) {
								var r = n[t];
								if (r !== e && r.form === e.form) {
									var i = r[qh] || null;
									if (!i) throw Error("ReactDOMInput: Mixing React and non-React radio inputs with the same `name` is not supported.");
									Ft(r, i.value, i.defaultValue, i.defaultValue, i.checked, i.defaultChecked, i.type, i.name);
								}
							}
							for (t = 0; t < n.length; t++) r = n[t], r.form === e.form && Mt(r);
						}
						break a;
					case "textarea":
						Ut(e, n.value, n.defaultValue);
						break a;
					case "select": t = n.value, t != null && Bt(e, !!n.multiple, t, !1);
				}
			}
		}
		function On(e, t, n) {
			if (e_) return e(t, n);
			e_ = !0;
			try {
				return e(t);
			} finally {
				if (e_ = !1, (Qg !== null || $g !== null) && (fu(), Qg && (t = Qg, e = $g, $g = Qg = null, Dn(t), e))) for (t = 0; t < e.length; t++) Dn(e[t]);
			}
		}
		function kn(e, t) {
			var n = e.stateNode;
			if (n === null) return null;
			var r = n[qh] || null;
			if (r === null) return null;
			n = r[t];
			a: switch (t) {
				case "onClick":
				case "onClickCapture":
				case "onDoubleClick":
				case "onDoubleClickCapture":
				case "onMouseDown":
				case "onMouseDownCapture":
				case "onMouseMove":
				case "onMouseMoveCapture":
				case "onMouseUp":
				case "onMouseUpCapture":
				case "onMouseEnter":
					(r = !r.disabled) || (e = e.type, r = e !== "button" && e !== "input" && e !== "select" && e !== "textarea"), e = !r;
					break a;
				default: e = !1;
			}
			if (e) return null;
			if (n && typeof n != "function") throw Error("Expected `" + t + "` listener to be a function, instead got a value of `" + typeof n + "` type.");
			return n;
		}
		function An() {
			if (o_) return o_;
			var e, t = a_, n = t.length, r, i = "value" in i_ ? i_.value : i_.textContent, a = i.length;
			for (e = 0; e < n && t[e] === i[e]; e++);
			var o = n - e;
			for (r = 1; r <= o && t[n - r] === i[a - r]; r++);
			return o_ = i.slice(e, 1 < r ? 1 - r : void 0);
		}
		function jn(e) {
			var t = e.keyCode;
			return "charCode" in e ? (e = e.charCode, e === 0 && t === 13 && (e = 13)) : e = t, e === 10 && (e = 13), 32 <= e || e === 13 ? e : 0;
		}
		function Mn() {
			return !0;
		}
		function Nn() {
			return !1;
		}
		function Pn(e) {
			function t(t, n, r, i, a) {
				for (var o in this._reactName = t, this._targetInst = r, this.type = n, this.nativeEvent = i, this.target = a, this.currentTarget = null, e) e.hasOwnProperty(o) && (t = e[o], this[o] = t ? t(i) : i[o]);
				return this.isDefaultPrevented = (i.defaultPrevented == null ? !1 === i.returnValue : i.defaultPrevented) ? Mn : Nn, this.isPropagationStopped = Nn, this;
			}
			return L(t.prototype, {
				preventDefault: function() {
					this.defaultPrevented = !0;
					var e = this.nativeEvent;
					e && (e.preventDefault ? e.preventDefault() : typeof e.returnValue != "unknown" && (e.returnValue = !1), this.isDefaultPrevented = Mn);
				},
				stopPropagation: function() {
					var e = this.nativeEvent;
					e && (e.stopPropagation ? e.stopPropagation() : typeof e.cancelBubble != "unknown" && (e.cancelBubble = !0), this.isPropagationStopped = Mn);
				},
				persist: function() {},
				isPersistent: Mn
			}), t;
		}
		function Fn(e) {
			var t = this.nativeEvent;
			return t.getModifierState ? t.getModifierState(e) : (e = w_[e]) ? !!t[e] : !1;
		}
		function In() {
			return Fn;
		}
		function Ln(e, t) {
			switch (e) {
				case "keyup": return M_.indexOf(t.keyCode) !== -1;
				case "keydown": return t.keyCode !== N_;
				case "keypress":
				case "mousedown":
				case "focusout": return !0;
				default: return !1;
			}
		}
		function Rn(e) {
			return e = e.detail, typeof e == "object" && "data" in e ? e.data : null;
		}
		function zn(e, t) {
			switch (e) {
				case "compositionend": return Rn(t);
				case "keypress": return t.which === R_ ? (B_ = !0, z_) : null;
				case "textInput": return e = t.data, e === z_ && B_ ? null : e;
				default: return null;
			}
		}
		function Bn(e, t) {
			if (V_) return e === "compositionend" || !P_ && Ln(e, t) ? (e = An(), o_ = a_ = i_ = null, V_ = !1, e) : null;
			switch (e) {
				case "paste": return null;
				case "keypress":
					if (!(t.ctrlKey || t.altKey || t.metaKey) || t.ctrlKey && t.altKey) {
						if (t.char && 1 < t.char.length) return t.char;
						if (t.which) return String.fromCharCode(t.which);
					}
					return null;
				case "compositionend": return L_ && t.locale !== "ko" ? null : t.data;
				default: return null;
			}
		}
		function Vn(e) {
			var t = e && e.nodeName && e.nodeName.toLowerCase();
			return t === "input" ? !!H_[e.type] : t === "textarea";
		}
		function Hn(e) {
			if (!t_) return !1;
			e = "on" + e;
			var t = e in document;
			return t ||= (t = document.createElement("div"), t.setAttribute(e, "return;"), typeof t[e] == "function"), t;
		}
		function Un(e, t, n, r) {
			Qg ? $g ? $g.push(r) : $g = [r] : Qg = r, t = Sd(t, "onChange"), 0 < t.length && (n = new c_("onChange", "change", null, n, r), e.push({
				event: n,
				listeners: t
			}));
		}
		function Wn(e) {
			gd(e, 0);
		}
		function Gn(e) {
			if (Mt(ht(e))) return e;
		}
		function Kn(e, t) {
			if (e === "change") return t;
		}
		function qn() {
			U_ && (U_.detachEvent("onpropertychange", Jn), W_ = U_ = null);
		}
		function Jn(e) {
			if (e.propertyName === "value" && Gn(W_)) {
				var t = [];
				Un(t, W_, e, En(e)), On(Wn, t);
			}
		}
		function Yn(e, t, n) {
			e === "focusin" ? (qn(), U_ = t, W_ = n, U_.attachEvent("onpropertychange", Jn)) : e === "focusout" && qn();
		}
		function Xn(e) {
			if (e === "selectionchange" || e === "keyup" || e === "keydown") return Gn(W_);
		}
		function Zn(e, t) {
			if (e === "click") return Gn(t);
		}
		function Qn(e, t) {
			if (e === "input" || e === "change") return Gn(t);
		}
		function $n(e, t) {
			return e === t && (e !== 0 || 1 / e == 1 / t) || e !== e && t !== t;
		}
		function er(e, t) {
			if (K_(e, t)) return !0;
			if (typeof e != "object" || !e || typeof t != "object" || !t) return !1;
			var n = Object.keys(e), r = Object.keys(t);
			if (n.length !== r.length) return !1;
			for (r = 0; r < n.length; r++) {
				var i = n[r];
				if (!_h.call(t, i) || !K_(e[i], t[i])) return !1;
			}
			return !0;
		}
		function tr(e) {
			if (e ||= typeof document < "u" ? document : void 0, e === void 0) return null;
			try {
				return e.activeElement || e.body;
			} catch {
				return e.body;
			}
		}
		function nr(e) {
			for (; e && e.firstChild;) e = e.firstChild;
			return e;
		}
		function rr(e, t) {
			var n = nr(e);
			e = 0;
			for (var r; n;) {
				if (n.nodeType === 3) {
					if (r = e + n.textContent.length, e <= t && r >= t) return {
						node: n,
						offset: t - e
					};
					e = r;
				}
				a: {
					for (; n;) {
						if (n.nextSibling) {
							n = n.nextSibling;
							break a;
						}
						n = n.parentNode;
					}
					n = void 0;
				}
				n = nr(n);
			}
		}
		function ir(e, t) {
			return e && t ? e === t ? !0 : e && e.nodeType === 3 ? !1 : t && t.nodeType === 3 ? ir(e, t.parentNode) : "contains" in e ? e.contains(t) : e.compareDocumentPosition ? !!(e.compareDocumentPosition(t) & 16) : !1 : !1;
		}
		function ar(e) {
			e = e != null && e.ownerDocument != null && e.ownerDocument.defaultView != null ? e.ownerDocument.defaultView : window;
			for (var t = tr(e.document); t instanceof e.HTMLIFrameElement;) {
				try {
					var n = typeof t.contentWindow.location.href == "string";
				} catch {
					n = !1;
				}
				if (n) e = t.contentWindow;
				else break;
				t = tr(e.document);
			}
			return t;
		}
		function or(e) {
			var t = e && e.nodeName && e.nodeName.toLowerCase();
			return t && (t === "input" && (e.type === "text" || e.type === "search" || e.type === "tel" || e.type === "url" || e.type === "password") || t === "textarea" || e.contentEditable === "true");
		}
		function sr(e, t, n) {
			var r = n.window === n ? n.document : n.nodeType === 9 ? n : n.ownerDocument;
			Z_ || J_ == null || J_ !== tr(r) || (r = J_, "selectionStart" in r && or(r) ? r = {
				start: r.selectionStart,
				end: r.selectionEnd
			} : (r = (r.ownerDocument && r.ownerDocument.defaultView || window).getSelection(), r = {
				anchorNode: r.anchorNode,
				anchorOffset: r.anchorOffset,
				focusNode: r.focusNode,
				focusOffset: r.focusOffset
			}), X_ && er(X_, r) || (X_ = r, r = Sd(Y_, "onSelect"), 0 < r.length && (t = new c_("onSelect", "select", null, t, n), e.push({
				event: t,
				listeners: r
			}), t.target = J_)));
		}
		function cr(e, t) {
			var n = {};
			return n[e.toLowerCase()] = t.toLowerCase(), n["Webkit" + e] = "webkit" + t, n["Moz" + e] = "moz" + t, n;
		}
		function lr(e) {
			if ($_[e]) return $_[e];
			if (!Q_[e]) return e;
			var t = Q_[e], n;
			for (n in t) if (t.hasOwnProperty(n) && n in ev) return $_[e] = t[n];
			return e;
		}
		function ur(e, t) {
			cv.set(e, t), yt(t, [e]);
		}
		function dr(e, t) {
			if (e.name != null && e.name !== "auto") return e.name;
			if (t.autoName !== null) return t.autoName;
			e = iw.identifierPrefix;
			var n = uv++;
			return e = "_" + e + "t_" + n.toString(32) + "_", t.autoName = e;
		}
		function fr(e) {
			if (e == null || typeof e == "string") return e;
			var t = null, n = pw;
			if (n !== null) for (var r = 0; r < n.length; r++) {
				var i = e[n[r]];
				if (i != null) {
					if (i === "none") return "none";
					t = t == null ? i : t + (" " + i);
				}
			}
			return t ?? e.default;
		}
		function pr(e, t) {
			return e = fr(e), t = fr(t), t == null ? e === "auto" ? null : e : t === "auto" ? null : t;
		}
		function mr(e) {
			for (var t = _v, n = 0; n < e.length && n < xv; n++) {
				var r = e[n];
				if (typeof r == "object" && r) {
					if (Jm(r) && r.length === 2 && typeof r[0] == "string") {
						if (t !== _v && t !== bv) return vv;
						t = bv;
					} else return vv;
				} else {
					if (typeof r == "function" || typeof r == "string" && 50 < r.length || t !== _v && t !== yv || typeof r == "bigint") return vv;
					t = yv;
				}
			}
			return t;
		}
		function hr(e, t, n, r) {
			if (!ArrayBuffer.isView(e)) {
				var i = 0, a;
				for (a in e) if (_h.call(e, a) && a[0] !== "_" && (i++, _r(a, e[a], t, n, r), i >= xv)) {
					t.push([r + "\xA0\xA0".repeat(n) + "Only " + xv + " properties are shown. React will not log more properties of this object.", ""]);
					break;
				}
			}
		}
		function gr(e) {
			return "$$typeof" in e && _h.call(e, "$$typeof") ? e.$$typeof : void 0;
		}
		function _r(e, t, n, r, i) {
			switch (typeof t) {
				case "object":
					if (t === null) {
						t = "null";
						break;
					}
					if (gr(t) === km) {
						var a = _e(t.type) || "…", o = t.key;
						t = t.props;
						var s = Object.keys(t), c = s.length;
						if (o == null && c === 0) {
							t = "<" + a + " />";
							break;
						}
						if (3 > r || c === 1 && s[0] === "children" && o == null) {
							t = "<" + a + " … />";
							break;
						}
						for (var l in n.push([i + "\xA0\xA0".repeat(r) + e, "<" + a]), o !== null && _r("key", o, n, r + 1, i), e = !1, o = 0, t) if (o++, l === "children" ? t.children != null && (!Jm(t.children) || 0 < t.children.length) && (e = !0) : _h.call(t, l) && l[0] !== "_" && _r(l, t[l], n, r + 1, i), o >= xv) break;
						n.push(["", e ? ">…</" + a + ">" : "/>"]);
						return;
					}
					if (a = Object.prototype.toString.call(t), a = a.slice(8, a.length - 1), ArrayBuffer.isView(t)) {
						t = t.length, t = typeof t == "number" ? a + "(" + t + ")" : a;
						break;
					}
					if (a === "Array") {
						if (l = t.length > xv, o = mr(t), o === yv || o === _v) {
							t = JSON.stringify(l ? t.slice(0, xv).concat("…") : t);
							break;
						}
						if (o === bv) {
							for (n.push([i + "\xA0\xA0".repeat(r) + e, ""]), e = 0; e < t.length && e < xv; e++) a = t[e], _r(a[0], a[1], n, r + 1, i);
							l && _r(xv.toString(), "…", n, r + 1, i);
							return;
						}
					}
					if (a === "Promise") {
						if (t.status === "fulfilled") {
							if (a = n.length, _r(e, t.value, n, r, i), n.length > a) {
								n = n[a], n[1] = "Promise<" + (n[1] || "Object") + ">";
								return;
							}
						} else if (t.status === "rejected" && (a = n.length, _r(e, t.reason, n, r, i), n.length > a)) {
							n = n[a], n[1] = "Rejected Promise<" + n[1] + ">";
							return;
						}
						n.push(["\xA0\xA0".repeat(r) + e, "Promise"]);
						return;
					}
					a === "Object" && (l = Object.getPrototypeOf(t)) && typeof l.constructor == "function" && (a = l.constructor.name), n.push([i + "\xA0\xA0".repeat(r) + e, a === "Object" ? 3 > r ? "" : "…" : a]), 3 > r && hr(t, n, r + 1, i);
					return;
				case "function":
					t = t.name, t = t === "" || typeof t != "string" ? "() => {}" : t + "() {}";
					break;
				case "string":
					t = t === gv ? "…" : JSON.stringify(1024 <= t.length ? t.slice(0, 1023) + "…" : t);
					break;
				case "undefined":
					t = "undefined";
					break;
				case "boolean":
					t = t ? "true" : "false";
					break;
				default: t = String(t);
			}
			n.push([i + "\xA0\xA0".repeat(r) + e, t]);
		}
		function vr(e, t, n, r) {
			var i = !0, a = 0;
			for (s in e) {
				if (a > xv) {
					n.push(["Previous object has more than " + xv + " properties. React will not attempt to diff objects with too many properties.", ""]), i = !1;
					break;
				}
				s in t || (n.push([Sv + "\xA0\xA0".repeat(r) + s, "…"]), i = !1), a++;
			}
			for (var o in a = 0, t) {
				if (a > xv) {
					n.push(["Next object has more than " + xv + " properties. React will not attempt to diff objects with too many properties.", ""]), i = !1;
					break;
				}
				if (o in e) {
					var s = e[o], c = t[o];
					if (s !== c) {
						if (r === 0 && o === "children") {
							i = "\xA0\xA0".repeat(r) + o, n.push([Sv + i, "…"], [Cv + i, "…"]), i = !1;
							continue;
						}
						if (!(3 <= r)) {
							if (typeof s == "object" && typeof c == "object" && s !== null && c !== null && gr(s) === gr(c)) {
								if (gr(c) === km) {
									if (s.type === c.type && s.key === c.key) {
										s = _e(c.type) || "…", i = "\xA0\xA0".repeat(r) + o, s = "<" + s + " … />", n.push([Sv + i, s], [Cv + i, s]), i = !1;
										continue;
									}
								} else {
									var l = Object.prototype.toString.call(s), u = Object.prototype.toString.call(c);
									if (l === u && (u === "[object Object]" || u === "[object Array]")) {
										l = [wv + "\xA0\xA0".repeat(r) + o, u === "[object Array]" ? "Array" : ""], n.push(l), u = n.length, vr(s, c, n, r + 1) ? u === n.length && (l[1] = "Referentially unequal but deeply equal objects. Consider memoization.") : i = !1;
										continue;
									}
								}
							} else if (typeof s == "function" && typeof c == "function" && s.name === c.name && s.length === c.length && (l = Function.prototype.toString.call(s), u = Function.prototype.toString.call(c), l === u)) {
								s = c.name === "" ? "() => {}" : c.name + "() {}", n.push([wv + "\xA0\xA0".repeat(r) + o, s + " Referentially unequal function closure. Consider memoization."]);
								continue;
							}
						}
						_r(o, s, n, r, Sv), _r(o, c, n, r, Cv), i = !1;
					}
				} else n.push([Cv + "\xA0\xA0".repeat(r) + o, "…"]), i = !1;
				a++;
			}
			return i;
		}
		function yr(e) {
			H = e & 63 ? "Blocking" : e & 64 ? "Gesture" : e & 4194176 ? "Transition" : e & 62914560 ? "Suspense" : e & 2080374784 ? "Idle" : "Other";
		}
		function br(e, t, n, r) {
			Tv && (kv.start = t, kv.end = n, Ov.color = "warning", Ov.tooltipText = r, Ov.properties = null, (e = e._debugTask) ? e.run(performance.measure.bind(performance, r, kv)) : performance.measure(r, kv), performance.clearMeasures(r));
		}
		function xr(e, t, n) {
			br(e, t, n, "Reconnect");
		}
		function Sr(e, t, n, r, i) {
			var a = S(e);
			if (a !== null && Tv) {
				var o = e.alternate, s = e.actualDuration;
				if (o === null || o.child !== e.child) for (var c = e.child; c !== null; c = c.sibling) s -= c.actualDuration;
				s = .5 > s ? r ? "tertiary-light" : "primary-light" : 10 > s ? r ? "tertiary" : "primary" : 100 > s ? r ? "tertiary-dark" : "primary-dark" : "error";
				var l = e.memoizedProps;
				r = e._debugTask, l !== null && o !== null && o.memoizedProps !== l ? (c = [Av], l = vr(o.memoizedProps, l, c, 0), 1 < c.length ? (l && !Dv && (o.lanes & i) === 0 && 100 < e.actualDuration ? (Dv = !0, c[0] = Mv, Ov.color = "warning", Ov.tooltipText = jv) : (Ov.color = s, Ov.tooltipText = a), Ov.properties = c, kv.start = t, kv.end = n, e = "​" + a, r == null ? performance.measure(e, kv) : r.run(performance.measure.bind(performance, e, kv)), performance.clearMeasures(e)) : r == null ? console.timeStamp(a, t, n, Ev, void 0, s) : r.run(console.timeStamp.bind(console, a, t, n, Ev, void 0, s))) : r == null ? console.timeStamp(a, t, n, Ev, void 0, s) : r.run(console.timeStamp.bind(console, a, t, n, Ev, void 0, s));
			}
		}
		function Cr(e, t, n, r) {
			if (Tv) {
				var i = S(e);
				if (i !== null) {
					for (var a = null, o = [], s = 0; s < r.length; s++) {
						var c = r[s];
						a == null && c.source !== null && (a = c.source._debugTask), c = c.value, o.push(["Error", typeof c == "object" && c && typeof c.message == "string" ? String(c.message) : String(c)]);
					}
					e.key !== null && _r("key", e.key, o, 0, ""), e.memoizedProps !== null && hr(e.memoizedProps, o, 0, ""), a ??= e._debugTask, e = {
						start: t,
						end: n,
						detail: { devtools: {
							color: "error",
							track: Ev,
							tooltipText: e.tag === 13 ? "Hydration failed" : "Error boundary caught an error",
							properties: o
						} }
					}, i = "​" + i, a ? a.run(performance.measure.bind(performance, i, e)) : performance.measure(i, e), performance.clearMeasures(i);
				}
			}
		}
		function wr(e, t, n, r, i) {
			if (i !== null) {
				if (Tv) {
					var a = S(e);
					if (a !== null) {
						r = [];
						for (var o = 0; o < i.length; o++) {
							var s = i[o].value;
							r.push(["Error", typeof s == "object" && s && typeof s.message == "string" ? String(s.message) : String(s)]);
						}
						e.key !== null && _r("key", e.key, r, 0, ""), e.memoizedProps !== null && hr(e.memoizedProps, r, 0, ""), t = {
							start: t,
							end: n,
							detail: { devtools: {
								color: "error",
								track: Ev,
								tooltipText: "A lifecycle or effect errored",
								properties: r
							} }
						}, e = e._debugTask, n = "​" + a, e ? e.run(performance.measure.bind(performance, n, t)) : performance.measure(n, t), performance.clearMeasures(n);
					}
				}
			} else a = S(e), a !== null && Tv && (i = 1 > r ? "secondary-light" : 100 > r ? "secondary" : 500 > r ? "secondary-dark" : "error", (e = e._debugTask) ? e.run(console.timeStamp.bind(console, a, t, n, Ev, void 0, i)) : console.timeStamp(a, t, n, Ev, void 0, i));
		}
		function Tr(e, t, n, r) {
			!Tv || t <= e || (n = (n & 738197653) === n ? "tertiary-dark" : "primary-dark", r ? r.run(console.timeStamp.bind(console, "Prewarm", e, t, H, V, n)) : console.timeStamp("Prewarm", e, t, H, V, n));
		}
		function Er(e, t, n, r) {
			!Tv || t <= e || (n = (n & 738197653) === n ? "tertiary-dark" : "primary-dark", r ? r.run(console.timeStamp.bind(console, "Suspended", e, t, H, V, n)) : console.timeStamp("Suspended", e, t, H, V, n));
		}
		function Dr(e, t, n, r) {
			!Tv || t <= e || (r ? r.run(console.timeStamp.bind(console, "Errored", e, t, H, V, "error")) : console.timeStamp("Errored", e, t, H, V, "error"));
		}
		function Or(e, t, n, r) {
			!Tv || t <= e || (r ? r.run(console.timeStamp.bind(console, n, e, t, H, V, "secondary-light")) : console.timeStamp(n, e, t, H, V, "secondary-light"));
		}
		function kr(e, t, n, r, i) {
			if (Tv && !(t <= e)) {
				for (var a = [], o = 0; o < n.length; o++) {
					var s = n[o].value;
					a.push(["Error", typeof s == "object" && s && typeof s.message == "string" ? String(s.message) : String(s)]);
				}
				e = {
					start: e,
					end: t,
					detail: { devtools: {
						color: "error",
						track: H,
						trackGroup: V,
						tooltipText: r ? "Remaining Effects Errored" : "Commit Errored",
						properties: a
					} }
				}, i ? i.run(performance.measure.bind(performance, "Errored", e)) : performance.measure("Errored", e), performance.clearMeasures("Errored");
			}
		}
		function Ar(e, t, n, r, i) {
			n === null ? !Tv || t <= e || (i ? i.run(console.timeStamp.bind(console, r ? "Commit Interrupted View Transition" : "Commit", e, t, H, V, r ? "error" : "secondary-dark")) : console.timeStamp(r ? "Commit Interrupted View Transition" : "Commit", e, t, H, V, r ? "error" : "secondary-dark")) : kr(e, t, n, !1, i);
		}
		function jr(e, t, n) {
			!Tv || t <= e || (n ? n.run(console.timeStamp.bind(console, "Animating", e, t, H, V, "secondary-dark")) : console.timeStamp("Animating", e, t, H, V, "secondary-dark"));
		}
		function Mr() {
			for (var e = Iv, t = Lv = Iv = 0; t < e;) {
				var n = Fv[t];
				Fv[t++] = null;
				var r = Fv[t];
				Fv[t++] = null;
				var i = Fv[t];
				Fv[t++] = null;
				var a = Fv[t];
				if (Fv[t++] = null, r !== null && i !== null) {
					var o = r.pending;
					o === null ? i.next = i : (i.next = o.next, o.next = i), r.pending = i;
				}
				a !== 0 && Ir(n, i, a);
			}
		}
		function Nr(e, t, n, r) {
			Fv[Iv++] = e, Fv[Iv++] = t, Fv[Iv++] = n, Fv[Iv++] = r, Lv |= r, e.lanes |= r, e = e.alternate, e !== null && (e.lanes |= r);
		}
		function Pr(e, t, n, r) {
			return Nr(e, t, n, r), Lr(e);
		}
		function Fr(e, t) {
			return Nr(e, null, null, t), Lr(e);
		}
		function Ir(e, t, n) {
			e.lanes |= n;
			var r = e.alternate;
			r !== null && (r.lanes |= n);
			for (var i = !1, a = e.return; a !== null;) a.childLanes |= n, r = a.alternate, r !== null && (r.childLanes |= n), a.tag === 22 && (e = a.stateNode, e === null || e._visibility & Nv || (i = !0)), e = a, a = a.return;
			return e.tag === 3 ? (a = e.stateNode, i && t !== null && (i = 31 - Fh(n), e = a.hiddenUpdates, r = e[i], r === null ? e[i] = [t] : r.push(t), t.lane = n | 536870912), a) : null;
		}
		function Lr(e) {
			if (vw > _w) throw Cw = vw = 0, ww = yw = null, Error("Maximum update depth exceeded. This can happen when a component repeatedly calls setState inside componentWillUpdate or componentDidUpdate. React limits the number of nested updates to prevent infinite loops.");
			Cw > Sw && (Cw = 0, ww = null, console.error("Maximum update depth exceeded. This can happen when a component calls setState inside useEffect, but useEffect either doesn't have a dependency array, or one of the dependencies changes on every render.")), e.alternate === null && e.flags & 4098 && td(e);
			for (var t = e, n = t.return; n !== null;) t.alternate === null && t.flags & 4098 && td(e), t = n, n = t.return;
			return t.tag === 3 ? t.stateNode : null;
		}
		function Rr(e) {
			if (zv === null) return e;
			var t = zv(e);
			return t === void 0 ? e : t.current;
		}
		function zr(e, t) {
			if (zv === null) return !1;
			var n = zv, r = e.elementType;
			t = t.type;
			var i = !1, a = typeof t == "object" && t ? t.$$typeof : null;
			switch (e.tag) {
				case 1:
					typeof t == "function" && (i = !0);
					break;
				case 0:
					(typeof t == "function" || a === Bm) && (i = !0);
					break;
				case 11:
					(a === Im || a === Bm) && (i = !0);
					break;
				case 14:
				case 15:
					(a === zm || a === Bm) && (i = !0);
					break;
				default: return !1;
			}
			return !!(i && (e = n(r), e !== void 0 && e === n(t)));
		}
		function Br(e) {
			zv !== null && typeof WeakSet == "function" && (Bv === null && (Bv = /* @__PURE__ */ new WeakSet()), Bv.add(e));
		}
		function Vr(e, t, n) {
			do {
				var r = e, i = r.alternate, a = r.child, o = r.sibling, s = r.tag, c = r.type, l = r.elementType, u = null;
				switch (r = null, s) {
					case 0:
					case 1:
						u = c;
						break;
					case 15:
						u = c, r = l;
						break;
					case 14:
						r = l;
						break;
					case 11: u = c.render, r = l;
				}
				if (zv === null) throw Error("Expected resolveFamily to be set during hot reload.");
				var d = zv;
				if (c = l = !1, u !== null && (u = d(u), u !== void 0 && (n.has(u) ? c = !0 : t.has(u) && (s === 1 ? c = !0 : l = !0))), c || r === null || (s = d(r), s !== void 0 && n.has(s) ? c = !0 : typeof r == "object" && r.$$typeof === Bm && (s = r._payload, s._status === 1 && (s = d(s._result.default), s !== void 0 && n.has(s) && (c = !0)))), Bv !== null && (Bv.has(e) || i !== null && Bv.has(i)) && (c = !0), c && (e._debugNeedsRemount = !0), (c || l) && (i = Fr(e, 2), i !== null && su(i, e, 2)), a === null || c || Vr(a, t, n), o === null) break;
				e = o;
			} while (1);
		}
		function Hr(e, t, n, r) {
			this.tag = e, this.key = n, this.sibling = this.child = this.return = this.stateNode = this.type = this.elementType = null, this.index = 0, this.refCleanup = this.ref = null, this.pendingProps = t, this.dependencies = this.memoizedState = this.updateQueue = this.memoizedProps = null, this.mode = r, this.subtreeFlags = this.flags = 0, this.deletions = null, this.childLanes = this.lanes = 0, this.alternate = null, this.actualDuration = -0, this.actualStartTime = -1.1, this.treeBaseDuration = this.selfBaseDuration = -0, this._debugTask = this._debugStack = this._debugOwner = this._debugInfo = null, this._debugNeedsRemount = !1, this._debugHookTypes = null, Gv || typeof Object.preventExtensions != "function" || Object.preventExtensions(this);
		}
		function Ur(e) {
			return e = e.prototype, !(!e || !e.isReactComponent);
		}
		function Wr(e, t) {
			var n = e.alternate;
			switch (n === null ? (n = g(e.tag, t, e.key, e.mode), n.elementType = e.elementType, n.type = e.type, n.stateNode = e.stateNode, n._debugOwner = e._debugOwner, n._debugStack = e._debugStack, n._debugTask = e._debugTask, n._debugHookTypes = e._debugHookTypes, n.alternate = e, e.alternate = n) : (n.pendingProps = t, n.type = e.type, n.flags = 0, n.subtreeFlags = 0, n.deletions = null, n.actualDuration = -0, n.actualStartTime = -1.1), n.flags = e.flags & 1206910976, n.childLanes = e.childLanes, n.lanes = e.lanes, n.child = e.child, n.memoizedProps = e.memoizedProps, n.memoizedState = e.memoizedState, n.updateQueue = e.updateQueue, t = e.dependencies, n.dependencies = t === null ? null : {
				lanes: t.lanes,
				firstContext: t.firstContext,
				_debugThenableState: t._debugThenableState
			}, n.sibling = e.sibling, n.index = e.index, n.ref = e.ref, n.refCleanup = e.refCleanup, n.selfBaseDuration = e.selfBaseDuration, n.treeBaseDuration = e.treeBaseDuration, n._debugInfo = e._debugInfo, n._debugNeedsRemount = e._debugNeedsRemount, n.tag) {
				case 0:
				case 15:
				case 14:
				case 1:
				case 11: n.type = Rr(e.type);
			}
			return n;
		}
		function Gr(e, t) {
			e.flags &= 1206910978;
			var n = e.alternate;
			return n === null ? (e.childLanes = 0, e.lanes = t, e.child = null, e.subtreeFlags = 0, e.memoizedProps = null, e.memoizedState = null, e.updateQueue = null, e.dependencies = null, e.stateNode = null, e.selfBaseDuration = 0, e.treeBaseDuration = 0) : (e.childLanes = n.childLanes, e.lanes = n.lanes, e.child = n.child, e.subtreeFlags = 0, e.deletions = null, e.memoizedProps = n.memoizedProps, e.memoizedState = n.memoizedState, e.updateQueue = n.updateQueue, e.type = n.type, t = n.dependencies, e.dependencies = t === null ? null : {
				lanes: t.lanes,
				firstContext: t.firstContext,
				_debugThenableState: t._debugThenableState
			}, e.selfBaseDuration = n.selfBaseDuration, e.treeBaseDuration = n.treeBaseDuration), e;
		}
		function Kr(e, t, n, r, i, a) {
			var o = 0, s = Rr(e);
			if (typeof s == "function") Ur(s) && (o = 1);
			else if (typeof s == "string") o = Te(), o = Ip(e, n, o) ? 26 : e === "html" || e === "head" || e === "body" ? 27 : 5;
			else a: switch (s) {
				case Vm: return t = g(31, n, t, i), t.elementType = Vm, t.lanes = a, t;
				case jm: return Jr(n.children, i, a, t);
				case Mm:
					o = 8, i |= Hv, i |= Uv;
					break;
				case Nm: return e = n, r = i, typeof e.id != "string" && console.error("Profiler must specify an \"id\" of type `string` as a prop. Received the type `%s` instead.", typeof e.id), t = g(12, e, t, r | W), t.elementType = Nm, t.lanes = a, t.stateNode = {
					effectDuration: 0,
					passiveEffectDuration: 0
				}, t;
				case Lm: return t = g(13, n, t, i), t.elementType = Lm, t.lanes = a, t;
				case Rm: return t = g(19, n, t, i), t.elementType = Rm, t.lanes = a, t;
				case Hm:
				case Wm: return e = i | Wv, t = g(30, n, t, e), t.elementType = Wm, t.lanes = a, t.stateNode = {
					autoName: null,
					paired: null,
					clones: null,
					ref: null
				}, t;
				default:
					if (typeof s == "object" && s) switch (s.$$typeof) {
						case Fm:
							o = 10;
							break a;
						case Pm:
							o = 9;
							break a;
						case Im:
							o = 11;
							break a;
						case zm:
							o = 14;
							break a;
						case Bm:
							o = 16, s = null;
							break a;
					}
					n = "", (e === void 0 || typeof e == "object" && e && Object.keys(e).length === 0) && (n += " You likely forgot to export your component from the file it's defined in, or you might have mixed up default and named imports."), e === null ? s = "null" : Jm(e) ? s = "array" : e !== void 0 && e.$$typeof === km ? (s = "<" + (_e(e.type) || "Unknown") + " />", n = " Did you accidentally export a JSX literal instead of a component?") : s = typeof e, (o = r ? ve(r) : null) && (n += "\n\nCheck the render method of `" + o + "`."), o = 29, n = Error("Element type is invalid: expected a string (for built-in components) or a class/function (for composite components) but got: " + (s + "." + n)), s = null;
			}
			return t = g(o, n, t, i), t.elementType = e, t.type = s, t.lanes = a, t._debugOwner = r, t;
		}
		function qr(e, t, n) {
			return t = Kr(e.type, e.key, e.props, e._owner, t, n), t._debugOwner = e._owner, t._debugStack = e._debugStack, t._debugTask = e._debugTask, t;
		}
		function Jr(e, t, n, r) {
			return e = g(7, e, r, t), e.lanes = n, e;
		}
		function Yr(e, t, n) {
			return e = g(6, e, null, t), e.lanes = n, e;
		}
		function Xr(e) {
			var t = g(18, null, null, U);
			return t.stateNode = e, t;
		}
		function Zr(e, t, n) {
			return t = g(4, e.children === null ? [] : e.children, e.key, t), t.lanes = n, t.stateNode = {
				containerInfo: e.containerInfo,
				pendingChildren: null,
				implementation: e.implementation
			}, t;
		}
		function Qr(e, t) {
			if (typeof e == "object" && e) {
				var n = Kv.get(e);
				return n === void 0 ? (t = {
					value: e,
					source: t,
					stack: Pe(t)
				}, Kv.set(e, t), t) : n;
			}
			return {
				value: e,
				source: t,
				stack: Pe(t)
			};
		}
		function $r(e, t) {
			ai(), qv[Jv++] = Xv, qv[Jv++] = Yv, Yv = e, Xv = t;
		}
		function ei(e, t, n) {
			ai(), Zv[Qv++] = ey, Zv[Qv++] = ty, Zv[Qv++] = $v, $v = e;
			var r = ey;
			e = ty;
			var i = 32 - Fh(r) - 1;
			r &= ~(1 << i), n += 1;
			var a = 32 - Fh(t) + i;
			if (30 < a) {
				var o = i - i % 5;
				a = (r & (1 << o) - 1).toString(32), r >>= o, i -= o, ey = 1 << 32 - Fh(t) + i | n << i | r, ty = a + e;
			} else ey = 1 << a | n << i | r, ty = e;
		}
		function ti(e) {
			ai(), e.return !== null && ($r(e, 1), ei(e, 1, 0));
		}
		function ni(e) {
			for (; e === Yv;) Yv = qv[--Jv], qv[Jv] = null, Xv = qv[--Jv], qv[Jv] = null;
			for (; e === $v;) $v = Zv[--Qv], Zv[Qv] = null, ty = Zv[--Qv], Zv[Qv] = null, ey = Zv[--Qv], Zv[Qv] = null;
		}
		function ri() {
			return ai(), $v === null ? null : {
				id: ey,
				overflow: ty
			};
		}
		function ii(e, t) {
			ai(), Zv[Qv++] = ey, Zv[Qv++] = ty, Zv[Qv++] = $v, ey = t.id, ty = t.overflow, $v = e;
		}
		function ai() {
			G || console.error("Expected to be hydrating. This is a bug in React. Please file an issue.");
		}
		function oi(e, t) {
			if (e.return === null) {
				if (ay === null) ay = {
					fiber: e,
					children: [],
					serverProps: void 0,
					serverTail: [],
					distanceFromLeaf: t
				};
				else {
					if (ay.fiber !== e) throw Error("Saw multiple hydration diff roots in a pass. This is a bug in React.");
					ay.distanceFromLeaf > t && (ay.distanceFromLeaf = t);
				}
				return ay;
			}
			var n = oi(e.return, t + 1).children;
			return 0 < n.length && n[n.length - 1].fiber === e ? (n = n[n.length - 1], n.distanceFromLeaf > t && (n.distanceFromLeaf = t), n) : (t = {
				fiber: e,
				children: [],
				serverProps: void 0,
				serverTail: [],
				distanceFromLeaf: t
			}, n.push(t), t);
		}
		function si() {
			G && console.error("We should not be hydrating here. This is a bug in React. Please file a bug.");
		}
		function ci(e, t) {
			iy || (e = oi(e, 0), e.serverProps = null, t !== null && (t = sp(t), e.serverTail.push(t)));
		}
		function li(e) {
			var t = 1 < arguments.length && arguments[1] !== void 0 && arguments[1], n = "", r = ay;
			throw r !== null && (ay = null, n = T(r)), hi(Qr(Error("Hydration failed because the server rendered " + (t ? "text" : "HTML") + " didn't match the client. As a result this tree will be regenerated on the client. This can happen if a SSR-ed Client Component used:\n\n- A server/client branch `if (typeof window !== 'undefined')`.\n- Variable input such as `Date.now()` or `Math.random()` which changes each time it's called.\n- Date formatting in a user's locale which doesn't match the server.\n- External changing data without sending a snapshot of it along with the HTML.\n- Invalid HTML tag nesting.\n\nIt can also happen if the client has a browser extension installed which messes with the HTML before React loaded.\n\nhttps://react.dev/link/hydration-mismatch" + n), e)), cy;
		}
		function ui(e) {
			var t = e.stateNode, n = e.type, r = e.memoizedProps;
			switch (t[Kh] = e, t[qh] = r, Td(n, r), n) {
				case "dialog":
					P("cancel", t), P("close", t);
					break;
				case "iframe":
				case "object":
				case "embed":
					P("load", t);
					break;
				case "video":
				case "audio":
					for (n = 0; n < zw.length; n++) P(zw[n], t);
					break;
				case "source":
					P("error", t);
					break;
				case "img":
				case "image":
				case "link":
					P("error", t), P("load", t);
					break;
				case "details":
					P("toggle", t);
					break;
				case "input":
					xt("input", r), P("invalid", t), Pt(t, r), It(t, r.value, r.defaultValue, r.checked, r.defaultChecked, r.type, r.name, !0);
					break;
				case "option":
					Rt(t, r);
					break;
				case "select":
					xt("select", r), P("invalid", t), Vt(t, r);
					break;
				case "textarea": xt("textarea", r), P("invalid", t), Ht(t, r), Wt(t, r.value, r.defaultValue, r.children);
			}
			n = r.children, typeof n != "string" && typeof n != "number" && typeof n != "bigint" || t.textContent === "" + n || !0 === r.suppressHydrationWarning || Nd(t.textContent, n) ? (r.popover != null && (P("beforetoggle", t), P("toggle", t)), r.onScroll != null && P("scroll", t), r.onScrollEnd != null && P("scrollend", t), r.onClick != null && (t.onclick = Tn), t = !0) : t = !1, t || li(e, !0);
		}
		function di(e) {
			for (ny = e.return; ny;) switch (ny.tag) {
				case 5:
				case 31:
				case 13:
					sy = !1;
					return;
				case 27:
				case 3:
					sy = !0;
					return;
				default: ny = ny.return;
			}
		}
		function fi(e) {
			if (e !== ny) return !1;
			if (!G) return di(e), G = !0, !1;
			var t = e.tag, n;
			if ((n = t !== 3 && t !== 27) && ((n = t === 5) && (n = e.type, n = n === "form" || n === "button" || ef(e.type, e.memoizedProps)), n = !n), n && ry) {
				for (n = ry; n;) {
					var r = oi(e, 0), i = sp(n);
					r.serverTail.push(i), n = i.type === "Suspense" ? lp(n) : op(n.nextSibling);
				}
				li(e);
			}
			if (di(e), t === 13) {
				if (e = e.memoizedState, e = e === null ? null : e.dehydrated, !e) throw Error("Expected to have a hydrated suspense instance. This error is likely caused by a bug in React. Please file an issue.");
				ry = lp(e);
			} else if (t === 31) {
				if (e = e.memoizedState, e = e === null ? null : e.dehydrated, !e) throw Error("Expected to have a hydrated suspense instance. This error is likely caused by a bug in React. Please file an issue.");
				ry = lp(e);
			} else t === 27 ? (t = ry, ff(e.type) ? (e = FT, FT = null, ry = e) : ry = t) : ry = ny ? op(e.stateNode.nextSibling) : null;
			return !0;
		}
		function pi() {
			ry = ny = null, iy = G = !1;
		}
		function mi() {
			var e = oy;
			return e !== null && (LC === null ? LC = e : LC.push.apply(LC, e), oy = null), e;
		}
		function hi(e) {
			oy === null ? oy = [e] : oy.push(e);
		}
		function gi() {
			var e = ay;
			if (e !== null) {
				ay = null;
				for (var t = T(e); 0 < e.children.length;) e = e.children[0];
				w(e.fiber, function() {
					console.error("A tree hydrated but some attributes of the server rendered HTML didn't match the client properties. This won't be patched up. This can happen if a SSR-ed Client Component used:\n\n- A server/client branch `if (typeof window !== 'undefined')`.\n- Variable input such as `Date.now()` or `Math.random()` which changes each time it's called.\n- Date formatting in a user's locale which doesn't match the server.\n- External changing data without sending a snapshot of it along with the HTML.\n- Invalid HTML tag nesting.\n\nIt can also happen if the client has a browser extension installed which messes with the HTML before React loaded.\n\n%s%s", "https://react.dev/link/hydration-mismatch", t);
				});
			}
		}
		function _i() {
			py = fy = null, my = !1;
		}
		function vi(e, t, n) {
			xe(ly, t._currentValue, e), t._currentValue = n, xe(uy, t._currentRenderer, e), t._currentRenderer !== void 0 && t._currentRenderer !== null && t._currentRenderer !== dy && console.error("Detected multiple renderers concurrently rendering the same context provider. This is currently unsupported."), t._currentRenderer = dy;
		}
		function yi(e, t) {
			e._currentValue = ly.current;
			var n = uy.current;
			be(uy, t), e._currentRenderer = n, be(ly, t);
		}
		function bi(e, t, n) {
			for (; e !== null;) {
				var r = e.alternate;
				if ((e.childLanes & t) === t ? r !== null && (r.childLanes & t) !== t && (r.childLanes |= t) : (e.childLanes |= t, r !== null && (r.childLanes |= t)), e === n) break;
				e = e.return;
			}
			e !== n && console.error("Expected to find the propagation root when scheduling context work. This error is likely caused by a bug in React. Please file an issue.");
		}
		function xi(e, t, n, r) {
			var i = e.child;
			for (i !== null && (i.return = e); i !== null;) {
				var a = i.dependencies;
				if (a !== null) {
					var o = i.child;
					a = a.firstContext;
					a: for (; a !== null;) {
						var s = a;
						a = i;
						for (var c = 0; c < t.length; c++) if (s.context === t[c]) {
							a.lanes |= n, s = a.alternate, s !== null && (s.lanes |= n), bi(a.return, n, e), r || (o = null);
							break a;
						}
						a = s.next;
					}
				} else if (i.tag === 18) {
					if (o = i.return, o === null) throw Error("We just came from a parent so we must have had a parent. This is a bug in React.");
					o.lanes |= n, a = o.alternate, a !== null && (a.lanes |= n), bi(o, n, e), o = null;
				} else i.tag === 13 && i.memoizedState !== null && i.memoizedState.dehydrated === null ? (i.lanes |= n, o = i.alternate, o !== null && (o.lanes |= n), bi(i.return, n, e), o = i.child, o = o === null ? null : o.sibling) : o = i.child;
				if (o !== null) o.return = i;
				else for (o = i; o !== null;) {
					if (o === e) {
						o = null;
						break;
					}
					if (i = o.sibling, i !== null) {
						i.return = o.return, o = i;
						break;
					}
					o = o.return;
				}
				i = o;
			}
		}
		function Si(e, t, n, r) {
			e = null;
			for (var i = t, a = !1; i !== null;) {
				if (!a) {
					if (i.flags & 524288) a = !0;
					else if (i.flags & 262144) break;
				}
				if (i.tag === 10) {
					var o = i.alternate;
					if (o === null) throw Error("Should have a current fiber. This is a bug in React.");
					if (o = o.memoizedProps, o !== null) {
						var s = i.type;
						K_(i.pendingProps.value, o.value) || (e === null ? e = [s] : e.push(s));
					}
				} else if (i === nh.current) {
					if (o = i.alternate, o === null) throw Error("Should have a current fiber. This is a bug in React.");
					o.memoizedState.memoizedState !== i.memoizedState.memoizedState && (e === null ? e = [$T] : e.push($T));
				}
				i = i.return;
			}
			return e !== null && xi(t, e, n, r), t.flags |= 262144, e !== null;
		}
		function Ci(e) {
			for (e = e.firstContext; e !== null;) {
				if (!K_(e.context._currentValue, e.memoizedValue)) return !0;
				e = e.next;
			}
			return !1;
		}
		function wi(e) {
			fy = e, py = null, e = e.dependencies, e !== null && (e.firstContext = null);
		}
		function Ti(e) {
			return my && console.error("Context can only be read while React is rendering. In classes, you can read it in the render method or getDerivedStateFromProps. In function components, you can read it directly in the function body, but not inside Hooks like useReducer() or useMemo()."), Di(fy, e);
		}
		function Ei(e, t) {
			return fy === null && wi(e), Di(e, t);
		}
		function Di(e, t) {
			var n = t._currentValue;
			if (t = {
				context: t,
				memoizedValue: n,
				next: null
			}, py === null) {
				if (e === null) throw Error("Context can only be read while React is rendering. In classes, you can read it in the render method or getDerivedStateFromProps. In function components, you can read it directly in the function body, but not inside Hooks like useReducer() or useMemo().");
				py = t, e.dependencies = {
					lanes: 0,
					firstContext: t,
					_debugThenableState: null
				}, e.flags |= 524288;
			} else py = py.next = t;
			return n;
		}
		function Oi() {
			return {
				controller: new hy(),
				data: /* @__PURE__ */ new Map(),
				refCount: 0
			};
		}
		function ki(e) {
			e.controller.signal.aborted && console.warn("A cache instance was retained after it was already freed. This likely indicates a bug in React."), e.refCount++;
		}
		function Ai(e) {
			e.refCount--, 0 > e.refCount && console.warn("A cache instance was released after it was already freed. This likely indicates a bug in React."), e.refCount === 0 && gy(_y, function() {
				e.controller.abort();
			});
		}
		function ji(e, t) {
			if (e.pendingLanes & 4194048) {
				var n = e.transitionTypes;
				for (n === null && (n = e.transitionTypes = []), e = 0; e < t.length; e++) {
					var r = t[e];
					n.indexOf(r) === -1 && n.push(r);
				}
			}
		}
		function Mi(e) {
			var t = e.transitionTypes;
			return e.transitionTypes = null, t;
		}
		function Ni(e, t, n) {
			e & 127 ? 0 > Py && (Py = by(), Fy = xy(t), Ly = t, n != null && (Ry = S(n)), (Z & (iC | aC)) !== rC && (My = !0, Iy = Sy), e = rf(), t = nf(), e !== Vy || t !== By ? Vy = -1.1 : t !== null && (Iy = Sy), zy = e, By = t) : e & 4194048 && 0 > Gy && (Gy = by(), qy = xy(t), Jy = t, n != null && (Yy = S(n)), 0 > Wy) && (e = rf(), t = nf(), (e !== Qy || t !== Zy) && (Qy = -1.1), Xy = e, Zy = t);
		}
		function Pi(e) {
			if (0 > Py) {
				Py = by(), Fy = e._debugTask == null ? null : e._debugTask, (Z & (iC | aC)) !== rC && (Iy = Sy);
				var t = rf(), n = nf();
				t !== Vy || n !== By ? Vy = -1.1 : n !== null && (Iy = Sy), zy = t, By = n;
			}
			0 > Gy && (Gy = by(), qy = e._debugTask == null ? null : e._debugTask, 0 > Wy) && (e = rf(), t = nf(), (e !== Qy || t !== Zy) && (Qy = -1.1), Xy = e, Zy = t);
		}
		function Fi() {
			var e = ky;
			return ky = 0, e;
		}
		function Ii(e) {
			var t = ky;
			return ky = e, t;
		}
		function Li(e) {
			var t = ky;
			return ky += e, t;
		}
		function Ri() {
			q = K = -1.1;
		}
		function zi() {
			var e = K;
			return K = -1.1, e;
		}
		function Bi(e) {
			0 <= e && (K = e);
		}
		function Vi() {
			var e = Ay;
			return Ay = -0, e;
		}
		function Hi(e) {
			0 <= e && (Ay = e);
		}
		function Ui() {
			var e = jy;
			return jy = null, e;
		}
		function Wi() {
			var e = My;
			return My = !1, e;
		}
		function Gi(e) {
			Oy = by(), 0 > e.actualStartTime && (e.actualStartTime = Oy);
		}
		function Ki(e) {
			if (0 <= Oy) {
				var t = by() - Oy;
				e.actualDuration += t, e.selfBaseDuration = t, Oy = -1;
			}
		}
		function qi(e) {
			if (0 <= Oy) {
				var t = by() - Oy;
				e.actualDuration += t, Oy = -1;
			}
		}
		function Ji() {
			if (0 <= Oy) {
				var e = by(), t = e - Oy;
				Oy = -1, ky += t, Ay += t, q = e;
			}
		}
		function Yi(e) {
			jy === null && (jy = []), jy.push(e), Dy === null && (Dy = []), Dy.push(e);
		}
		function Xi() {
			Oy = by(), 0 > K && (K = Oy);
		}
		function Zi(e) {
			for (var t = e.child; t;) e.actualDuration += t.actualDuration, t = t.sibling;
		}
		function Qi(e, t) {
			if (cb === null) {
				var n = cb = [];
				lb = 0, ub = md(), db = {
					status: "pending",
					value: void 0,
					then: function(e) {
						n.push(e);
					}
				};
			}
			return lb++, t.then($i, $i), t;
		}
		function $i() {
			if (--lb === 0 && (-1 < Gy || (Wy = -1.1), yy = null, cb !== null)) {
				db !== null && (db.status = "fulfilled");
				var e = cb;
				cb = null, ub = 0, db = null;
				for (var t = 0; t < e.length; t++) (0, e[t])();
			}
		}
		function ea(e, t) {
			var n = [], r = {
				status: "pending",
				value: null,
				reason: null,
				then: function(e) {
					n.push(e);
				}
			};
			return e.then(function() {
				r.status = "fulfilled", r.value = t;
				for (var e = 0; e < n.length; e++) (0, n[e])(t);
			}, function(e) {
				for (r.status = "rejected", r.reason = e, e = 0; e < n.length; e++) (0, n[e])(void 0);
			}), r;
		}
		function ta() {
			var e = pb.current;
			return e === null ? pC.pooledCache : e;
		}
		function na(e, t) {
			t === null ? xe(pb, pb.current, e) : xe(pb, t.pool, e);
		}
		function ra() {
			var e = ta();
			return e === null ? null : {
				parent: vy._currentValue,
				pool: e
			};
		}
		function ia() {
			return {
				didWarnAboutUncachedPromise: !1,
				thenables: []
			};
		}
		function aa(e) {
			return e = e.status, e === "fulfilled" || e === "rejected";
		}
		function oa(e, t, n, r) {
			R.actQueue !== null && (R.didUsePromise = !0);
			var i = e.thenables;
			if (n = i[n], n === void 0 ? i.push(t) : n !== t && (e.didWarnAboutUncachedPromise || (e.didWarnAboutUncachedPromise = !0, console.error("A component was suspended by an uncached promise. Creating promises inside a Client Component or hook is not yet supported, except via a Suspense-compatible library or framework.")), t.then(Tn, Tn), t = n), t._debugInfo === void 0) {
				e = performance.now(), i = t.displayName;
				var a = {
					name: typeof i == "string" ? i : "Promise",
					start: e,
					end: e,
					value: t
				};
				t._debugInfo = [{ awaited: a }], t.status !== "fulfilled" && t.status !== "rejected" && (e = function() {
					a.end = performance.now();
				}, t.then(e, e));
			}
			switch (t.status) {
				case "fulfilled": return t.value;
				case "rejected": throw r = t.reason, la(r), r === void 0 && !("reason" in t) ? Error("A rejected Promise was passed to React without a `reason` property. React threw a generic error from where the Promise was used to assist in identifying the problematic Promise. Make sure that instrumented Promises correctly set the `reason` property when setting `status` to `'rejected'`.") : r;
				default:
					if (typeof t.status == "string") t.then(Tn, Tn);
					else {
						if (e = pC, e !== null && 100 < e.shellSuspendCounter) throw Error("An unknown Component is an async Client Component. Only Server Components can be async at the moment. This error is often caused by accidentally adding `'use client'` to a module that was originally written for the server.");
						e = t, e.status = "pending", e.then(function(e) {
							if (t.status === "pending") {
								var n = t;
								n.status = "fulfilled", n.value = e;
							}
						}, function(e) {
							if (t.status === "pending") {
								var n = t;
								n.status = "rejected", n.reason = e;
							}
						});
					}
					switch (t.status) {
						case "fulfilled": return t.value;
						case "rejected": throw r = t.reason, la(r), r;
					}
					throw Xb = t, Zb = !0, Yb || r === null || r.alternate !== null || (qb = r, Jb = Error("This library called use() to suspend in a previous render but did not call use() when it finished. This indicates an incorrect use of use(). Learn more: https://react.dev/warnings/conditional-use-of-use")), Ub;
			}
		}
		function sa(e) {
			try {
				return Hb(e);
			} catch (e) {
				throw typeof e == "object" && e && typeof e.then == "function" ? (Xb = e, Zb = !0, Ub) : e;
			}
		}
		function ca() {
			if (Xb === null) throw Error("Expected a suspended thenable. This is a bug in React. Please file an issue.");
			var e = Xb;
			return Xb = null, Zb = !1, e;
		}
		function la(e) {
			if (e === Ub || e === Gb) throw Error("Hooks are not supported inside an async component. This error is often caused by accidentally adding `'use client'` to a module that was originally written for the server.");
		}
		function ua(e, t) {
			return e === t ? !0 : e.tag !== t.tag || e.type !== t.type || e.key !== t.key || e.index !== t.index || e.tag === 3 && e.stateNode !== t.stateNode || e.return === null || t.return === null ? !1 : ua(e.return, t.return);
		}
		function da(e) {
			var t = J;
			return e != null && (J = t === null ? e : t.concat(e)), t;
		}
		function fa() {
			var e = J;
			if (e != null) {
				for (var t = e.length - 1; 0 <= t; t--) if (e[t].name != null) {
					var n = e[t].debugTask;
					if (n != null) return n;
				}
			}
			return null;
		}
		function pa(e, t, n) {
			for (var r = Object.keys(e.props), i = 0; i < r.length; i++) {
				var a = r[i];
				if (a !== "children" && a !== "key" && a !== "ref") {
					t === null && (t = qr(e, n.mode, 0), t._debugInfo = J, t.return = n), w(t, function(e) {
						console.error("Invalid prop `%s` supplied to `React.Fragment`. React.Fragment can only have `key`, `ref`, and `children` props.", e);
					}, a);
					break;
				}
			}
		}
		function ma(e) {
			var t = $b;
			return $b += 1, Qb === null && (Qb = ia()), oa(Qb, e, t, null);
		}
		function ha(e, t) {
			t = t.props.ref, e.ref = t === void 0 ? null : t;
		}
		function ga(e, t) {
			throw t.$$typeof === Om ? Error("A React Element from an older version of React was rendered. This is not supported. It can happen if:\n- Multiple copies of the \"react\" package is used.\n- A library pre-bundled an old copy of \"react\" or \"react/jsx-runtime\".\n- A compiler tries to \"inline\" JSX instead of using the runtime.") : (e = Object.prototype.toString.call(t), Error("Objects are not valid as a React child (found: " + (e === "[object Object]" ? "object with keys {" + Object.keys(t).join(", ") + "}" : e) + "). If you meant to render a collection of children, use an array instead."));
		}
		function _a(e, t) {
			var n = fa();
			n === null ? ga(e, t) : n.run(ga.bind(null, e, t));
		}
		function va(e, t) {
			var n = S(e) || "Component";
			rx[n] || (rx[n] = !0, t = t.displayName || t.name || "Component", e.tag === 3 ? console.error("Functions are not valid as a React child. This may happen if you return %s instead of <%s /> from render. Or maybe you meant to call this function rather than return it.\n  root.render(%s)", t, t, t) : console.error("Functions are not valid as a React child. This may happen if you return %s instead of <%s /> from render. Or maybe you meant to call this function rather than return it.\n  <%s>{%s}</%s>", t, t, n, t, n));
		}
		function E(e, t) {
			var n = fa();
			n === null ? va(e, t) : n.run(va.bind(null, e, t));
		}
		function ya(e, t) {
			var n = S(e) || "Component";
			ix[n] || (ix[n] = !0, t = String(t), e.tag === 3 ? console.error("Symbols are not valid as a React child.\n  root.render(%s)", t) : console.error("Symbols are not valid as a React child.\n  <%s>%s</%s>", n, t, n));
		}
		function ba(e, t) {
			var n = fa();
			n === null ? ya(e, t) : n.run(ya.bind(null, e, t));
		}
		function xa(e) {
			function t(t, n) {
				if (e) {
					var r = t.deletions;
					r === null ? (t.deletions = [n], t.flags |= 16) : r.push(n);
				}
			}
			function n(n, r) {
				if (!e) return null;
				for (; r !== null;) t(n, r), r = r.sibling;
				return null;
			}
			function r(e) {
				for (var t = /* @__PURE__ */ new Map(); e !== null;) e.key === null ? t.set(e.index, e) : t.set(e.key, e), e = e.sibling;
				return t;
			}
			function i(e, t) {
				return e = Wr(e, t), e.index = 0, e.sibling = null, e;
			}
			function a(t, n, r) {
				return t.index = r, e ? (r = t.alternate, r === null ? (t.flags |= 134217730, n) : (r = r.index, r < n ? (t.flags |= 2, n) : r)) : (t.flags |= 1048576, n);
			}
			function o(t) {
				return e && t.alternate === null && (t.flags |= 134217730), t;
			}
			function s(e, t, n, r) {
				return t === null || t.tag !== 6 ? (t = Yr(n, e.mode, r), t.return = e, t._debugOwner = e, t._debugTask = e._debugTask, t._debugInfo = J, t) : (t = i(t, n), t.return = e, t._debugInfo = J, t);
			}
			function c(e, t, n, r) {
				var a = n.type;
				return a === jm ? (t = u(e, t, n.props.children, r, n.key), ha(t, n), pa(n, t, e), t) : t !== null && (t.elementType === a || zr(t, n) || typeof a == "object" && a && a.$$typeof === Bm && sa(a) === t.type) ? (t = i(t, n.props), ha(t, n), t.return = e, t._debugOwner = n._owner, t._debugInfo = J, t) : (t = qr(n, e.mode, r), ha(t, n), t.return = e, t._debugInfo = J, t);
			}
			function l(e, t, n, r) {
				return t === null || t.tag !== 4 || t.stateNode.containerInfo !== n.containerInfo || t.stateNode.implementation !== n.implementation ? (t = Zr(n, e.mode, r), t.return = e, t._debugInfo = J, t) : (t = i(t, n.children || []), t.return = e, t._debugInfo = J, t);
			}
			function u(e, t, n, r, a) {
				return t === null || t.tag !== 7 ? (t = Jr(n, e.mode, r, a), t.return = e, t._debugOwner = e, t._debugTask = e._debugTask, t._debugInfo = J, t) : (t = i(t, n), t.return = e, t._debugInfo = J, t);
			}
			function d(e, t, n) {
				if (typeof t == "string" && t !== "" || typeof t == "number" || typeof t == "bigint") return t = Yr("" + t, e.mode, n), t.return = e, t._debugOwner = e, t._debugTask = e._debugTask, t._debugInfo = J, t;
				if (typeof t == "object" && t) {
					switch (t.$$typeof) {
						case km: return n = qr(t, e.mode, n), ha(n, t), n.return = e, e = da(t._debugInfo), n._debugInfo = J, J = e, n;
						case Am: return t = Zr(t, e.mode, n), t.return = e, t._debugInfo = J, t;
						case Bm:
							var r = da(t._debugInfo);
							return t = sa(t), e = d(e, t, n), J = r, e;
					}
					if (Jm(t) || ge(t)) return n = Jr(t, e.mode, n, null), n.return = e, n._debugOwner = e, n._debugTask = e._debugTask, e = da(t._debugInfo), n._debugInfo = J, J = e, n;
					if (typeof t.then == "function") return r = da(t._debugInfo), e = d(e, ma(t), n), J = r, e;
					if (t.$$typeof === Fm) return d(e, Ei(e, t), n);
					_a(e, t);
				}
				return typeof t == "function" && E(e, t), typeof t == "symbol" && ba(e, t), null;
			}
			function f(e, t, n, r) {
				var i = t === null ? null : t.key;
				if (typeof n == "string" && n !== "" || typeof n == "number" || typeof n == "bigint") return i === null ? s(e, t, "" + n, r) : null;
				if (typeof n == "object" && n) {
					switch (n.$$typeof) {
						case km: return n.key === i ? (i = da(n._debugInfo), e = c(e, t, n, r), J = i, e) : null;
						case Am: return n.key === i ? l(e, t, n, r) : null;
						case Bm: return i = da(n._debugInfo), n = sa(n), e = f(e, t, n, r), J = i, e;
					}
					if (Jm(n) || ge(n)) return i === null ? (i = da(n._debugInfo), e = u(e, t, n, r, null), J = i, e) : null;
					if (typeof n.then == "function") return i = da(n._debugInfo), e = f(e, t, ma(n), r), J = i, e;
					if (n.$$typeof === Fm) return f(e, t, Ei(e, n), r);
					_a(e, n);
				}
				return typeof n == "function" && E(e, n), typeof n == "symbol" && ba(e, n), null;
			}
			function p(e, t, n, r, i) {
				if (typeof r == "string" && r !== "" || typeof r == "number" || typeof r == "bigint") return e = e.get(n) || null, s(t, e, "" + r, i);
				if (typeof r == "object" && r) {
					switch (r.$$typeof) {
						case km: return n = e.get(r.key === null ? n : r.key) || null, e = da(r._debugInfo), t = c(t, n, r, i), J = e, t;
						case Am: return e = e.get(r.key === null ? n : r.key) || null, l(t, e, r, i);
						case Bm:
							var a = da(r._debugInfo);
							return r = sa(r), t = p(e, t, n, r, i), J = a, t;
					}
					if (Jm(r) || ge(r)) return n = e.get(n) || null, e = da(r._debugInfo), t = u(t, n, r, i, null), J = e, t;
					if (typeof r.then == "function") return a = da(r._debugInfo), t = p(e, t, n, ma(r), i), J = a, t;
					if (r.$$typeof === Fm) return p(e, t, n, Ei(t, r), i);
					_a(t, r);
				}
				return typeof r == "function" && E(t, r), typeof r == "symbol" && ba(t, r), null;
			}
			function h(e, t, n, r) {
				if (typeof n != "object" || !n) return r;
				switch (n.$$typeof) {
					case km:
					case Am:
						m(e, t, n);
						var i = n.key;
						if (typeof i != "string") break;
						if (r === null) {
							r = /* @__PURE__ */ new Set(), r.add(i);
							break;
						}
						if (!r.has(i)) {
							r.add(i);
							break;
						}
						w(t, function() {
							console.error("Encountered two children with the same key, `%s`. Keys should be unique so that components maintain their identity across updates. Non-unique keys may cause children to be duplicated and/or omitted — the behavior is unsupported and could change in a future version.", i);
						});
						break;
					case Bm: n = sa(n), h(e, t, n, r);
				}
				return r;
			}
			function _(i, o, s, c) {
				for (var l = null, u = null, m = null, g = o, _ = o = 0, v = null; g !== null && _ < s.length; _++) {
					g.index > _ ? (v = g, g = null) : v = g.sibling;
					var y = f(i, g, s[_], c);
					if (y === null) {
						g === null && (g = v);
						break;
					}
					l = h(i, y, s[_], l), e && g && y.alternate === null && t(i, g), o = a(y, o, _), m === null ? u = y : m.sibling = y, m = y, g = v;
				}
				if (_ === s.length) return n(i, g), G && $r(i, _), u;
				if (g === null) {
					for (; _ < s.length; _++) g = d(i, s[_], c), g !== null && (l = h(i, g, s[_], l), o = a(g, o, _), m === null ? u = g : m.sibling = g, m = g);
					return G && $r(i, _), u;
				}
				for (g = r(g); _ < s.length; _++) v = p(g, i, _, s[_], c), v !== null && (l = h(i, v, s[_], l), e && (y = v.alternate, y !== null && g.delete(y.key === null ? _ : y.key)), o = a(v, o, _), m === null ? u = v : m.sibling = v, m = v);
				return e && g.forEach(function(e) {
					return t(i, e);
				}), G && $r(i, _), u;
			}
			function v(i, o, s, c) {
				if (s == null) throw Error("An iterable object provided no iterator.");
				for (var l = null, u = null, m = o, g = o = 0, _ = null, v = null, y = s.next(); m !== null && !y.done; g++, y = s.next()) {
					m.index > g ? (_ = m, m = null) : _ = m.sibling;
					var b = f(i, m, y.value, c);
					if (b === null) {
						m === null && (m = _);
						break;
					}
					v = h(i, b, y.value, v), e && m && b.alternate === null && t(i, m), o = a(b, o, g), u === null ? l = b : u.sibling = b, u = b, m = _;
				}
				if (y.done) return n(i, m), G && $r(i, g), l;
				if (m === null) {
					for (; !y.done; g++, y = s.next()) m = d(i, y.value, c), m !== null && (v = h(i, m, y.value, v), o = a(m, o, g), u === null ? l = m : u.sibling = m, u = m);
					return G && $r(i, g), l;
				}
				for (m = r(m); !y.done; g++, y = s.next()) _ = p(m, i, g, y.value, c), _ !== null && (v = h(i, _, y.value, v), e && (y = _.alternate, y !== null && m.delete(y.key === null ? g : y.key)), o = a(_, o, g), u === null ? l = _ : u.sibling = _, u = _);
				return e && m.forEach(function(e) {
					return t(i, e);
				}), G && $r(i, g), l;
			}
			function y(e, r, a, s) {
				if (typeof a == "object" && a && a.type === jm && a.key === null && a.props.ref === void 0 && (pa(a, null, e), a = a.props.children), typeof a == "object" && a) {
					switch (a.$$typeof) {
						case km:
							var c = da(a._debugInfo);
							a: {
								for (var l = a.key; r !== null;) {
									if (r.key === l) {
										if (l = a.type, l === jm) {
											if (r.tag === 7) {
												n(e, r.sibling), s = i(r, a.props.children), ha(s, a), s.return = e, s._debugOwner = a._owner, s._debugInfo = J, pa(a, s, e), e = s;
												break a;
											}
										} else if (r.elementType === l || zr(r, a) || typeof l == "object" && l && l.$$typeof === Bm && sa(l) === r.type) {
											n(e, r.sibling), s = i(r, a.props), ha(s, a), s.return = e, s._debugOwner = a._owner, s._debugInfo = J, e = s;
											break a;
										}
										n(e, r);
										break;
									}
									t(e, r), r = r.sibling;
								}
								a.type === jm ? (s = Jr(a.props.children, e.mode, s, a.key), ha(s, a), s.return = e, s._debugOwner = e, s._debugTask = e._debugTask, s._debugInfo = J, pa(a, s, e), e = s) : (s = qr(a, e.mode, s), ha(s, a), s.return = e, s._debugInfo = J, e = s);
							}
							return e = o(e), J = c, e;
						case Am:
							a: {
								for (c = a, a = c.key; r !== null;) {
									if (r.key === a) {
										if (r.tag === 4 && r.stateNode.containerInfo === c.containerInfo && r.stateNode.implementation === c.implementation) {
											n(e, r.sibling), s = i(r, c.children || []), s.return = e, e = s;
											break a;
										}
										n(e, r);
										break;
									}
									t(e, r), r = r.sibling;
								}
								s = Zr(c, e.mode, s), s.return = e, e = s;
							}
							return o(e);
						case Bm: return c = da(a._debugInfo), a = sa(a), e = y(e, r, a, s), J = c, e;
					}
					if (Jm(a)) return _(e, r, a, s);
					if (ge(a)) {
						if (c = a, a = ge(c), typeof a != "function") throw Error("An object is not an iterable. This error is likely caused by a bug in React. Please file an issue.");
						return l = a.call(c), l === c ? (e.tag !== 0 || Object.prototype.toString.call(e.type) !== "[object GeneratorFunction]" || Object.prototype.toString.call(l) !== "[object Generator]") && (tx || console.error("Using Iterators as children is unsupported and will likely yield unexpected results because enumerating a generator mutates it. You may convert it to an array with `Array.from()` or the `[...spread]` operator before rendering. You can also use an Iterable that can iterate multiple times over the same items."), tx = !0) : c.entries !== a || ex || (console.error("Using Maps as children is not supported. Use an array of keyed ReactElements instead."), ex = !0), v(e, r, l, s);
					}
					if (typeof a.then == "function") return c = da(a._debugInfo), e = y(e, r, ma(a), s), J = c, e;
					if (a.$$typeof === Fm) return y(e, r, Ei(e, a), s);
					_a(e, a);
				}
				return typeof a == "string" && a !== "" || typeof a == "number" || typeof a == "bigint" ? (c = "" + a, r !== null && r.tag === 6 ? (n(e, r.sibling), s = i(r, c), s.return = e, e = s) : (n(e, r), s = Yr(c, e.mode, s), s.return = e, s._debugOwner = e, s._debugTask = e._debugTask, s._debugInfo = J, e = s), o(e)) : (typeof a == "function" && E(e, a), typeof a == "symbol" && ba(e, a), n(e, r));
			}
			return function(e, t, n, r) {
				var i = J;
				J = null;
				try {
					$b = 0;
					var a = y(e, t, n, r);
					return Qb = null, a;
				} catch (t) {
					if (t === Ub || t === Gb) throw t;
					var o = g(29, t, null, e.mode);
					o.lanes = r, o.return = e;
					var s = o._debugInfo = J;
					if (o._debugOwner = e._debugOwner, o._debugTask = e._debugTask, s != null) {
						for (var c = s.length - 1; 0 <= c; c--) if (typeof s[c].stack == "string") {
							o._debugOwner = s[c], o._debugTask = s[c].debugTask;
							break;
						}
					}
					return o;
				} finally {
					J = i;
				}
			};
		}
		function Sa(e, t) {
			var n = Jm(e);
			return e = !n && typeof ge(e) == "function", n || e ? (n = n ? "array" : "iterable", console.error("A nested %s was passed to row #%s in <SuspenseList />. Wrap it in an additional SuspenseList to configure its revealOrder: <SuspenseList revealOrder=...> ... <SuspenseList revealOrder=...>{%s}</SuspenseList> ... </SuspenseList>", n, t, n), !1) : !0;
		}
		function Ca(e) {
			e.updateQueue = {
				baseState: e.memoizedState,
				firstBaseUpdate: null,
				lastBaseUpdate: null,
				shared: {
					pending: null,
					lanes: 0,
					hiddenCallbacks: null
				},
				callbacks: null
			};
		}
		function wa(e, t) {
			e = e.updateQueue, t.updateQueue === e && (t.updateQueue = {
				baseState: e.baseState,
				firstBaseUpdate: e.firstBaseUpdate,
				lastBaseUpdate: e.lastBaseUpdate,
				shared: e.shared,
				callbacks: null
			});
		}
		function Ta(e) {
			return {
				lane: e,
				tag: sx,
				payload: null,
				callback: null,
				next: null
			};
		}
		function Ea(e, t, n) {
			var r = e.updateQueue;
			if (r === null) return null;
			if (r = r.shared, px === r && !fx) {
				var i = S(e);
				console.error("An update (setState, replaceState, or forceUpdate) was scheduled from inside an update function. Update functions should be pure, with zero side-effects. Consider using componentDidUpdate or a callback.\n\nPlease update the following component: %s", i), fx = !0;
			}
			return (Z & iC) === rC ? (Nr(e, r, t, n), Lr(e)) : (i = r.pending, i === null ? t.next = t : (t.next = i.next, i.next = t), r.pending = t, t = Lr(e), Ir(e, null, n), t);
		}
		function Da(e, t, n) {
			if (t = t.updateQueue, t !== null && (t = t.shared, n & 4194048)) {
				var r = t.lanes;
				r &= e.pendingLanes, n |= r, t.lanes = n, it(e, n);
			}
		}
		function Oa(e, t) {
			var n = e.updateQueue, r = e.alternate;
			if (r !== null && (r = r.updateQueue, n === r)) {
				var i = null, a = null;
				if (n = n.firstBaseUpdate, n !== null) {
					do {
						var o = {
							lane: n.lane,
							tag: n.tag,
							payload: n.payload,
							callback: null,
							next: null
						};
						a === null ? i = a = o : a = a.next = o, n = n.next;
					} while (n !== null);
					a === null ? i = a = t : a = a.next = t;
				} else i = a = t;
				n = {
					baseState: r.baseState,
					firstBaseUpdate: i,
					lastBaseUpdate: a,
					shared: r.shared,
					callbacks: r.callbacks
				}, e.updateQueue = n;
			} else e = n.lastBaseUpdate, e === null ? n.firstBaseUpdate = t : e.next = t, n.lastBaseUpdate = t;
		}
		function ka() {
			if (mx) {
				var e = db;
				if (e !== null) throw e;
			}
		}
		function Aa(e, t, n, r) {
			mx = !1;
			var i = e.updateQueue;
			dx = !1, px = i.shared;
			var a = i.firstBaseUpdate, o = i.lastBaseUpdate, s = i.shared.pending;
			if (s !== null) {
				i.shared.pending = null;
				var c = s, l = c.next;
				c.next = null, o === null ? a = l : o.next = l, o = c;
				var u = e.alternate;
				u !== null && (u = u.updateQueue, s = u.lastBaseUpdate, s !== o && (s === null ? u.firstBaseUpdate = l : s.next = l, u.lastBaseUpdate = c));
			}
			if (a !== null) {
				var d = i.baseState;
				o = 0, u = l = c = null, s = a;
				do {
					var f = s.lane & -536870913, p = f !== s.lane;
					if (p ? ($ & f) === f : (r & f) === f) {
						f !== 0 && f === ub && (mx = !0), u !== null && (u = u.next = {
							lane: 0,
							tag: s.tag,
							payload: s.payload,
							callback: null,
							next: null
						});
						a: {
							f = e;
							var m = s, h = t, g = n;
							switch (m.tag) {
								case cx:
									if (m = m.payload, typeof m == "function") {
										my = !0;
										var _ = m.call(g, d, h);
										if (f.mode & Hv) {
											Ke(!0);
											try {
												m.call(g, d, h);
											} finally {
												Ke(!1);
											}
										}
										my = !1, d = _;
										break a;
									}
									d = m;
									break a;
								case ux: f.flags = f.flags & -65537 | 128;
								case sx:
									if (_ = m.payload, typeof _ == "function") {
										if (my = !0, m = _.call(g, d, h), f.mode & Hv) {
											Ke(!0);
											try {
												_.call(g, d, h);
											} finally {
												Ke(!1);
											}
										}
										my = !1;
									} else m = _;
									if (m == null) break a;
									d = L({}, d, m);
									break a;
								case lx: dx = !0;
							}
						}
						f = s.callback, f !== null && (e.flags |= 64, p && (e.flags |= 8192), p = i.callbacks, p === null ? i.callbacks = [f] : p.push(f));
					} else p = {
						lane: f,
						tag: s.tag,
						payload: s.payload,
						callback: s.callback,
						next: null
					}, u === null ? (l = u = p, c = d) : u = u.next = p, o |= f;
					if (s = s.next, s === null) {
						if (s = i.shared.pending, s === null) break;
						p = s, s = p.next, p.next = null, i.lastBaseUpdate = p, i.shared.pending = null;
					}
				} while (1);
				u === null && (c = d), i.baseState = c, i.firstBaseUpdate = l, i.lastBaseUpdate = u, a === null && (i.shared.lanes = 0), jC |= o, e.lanes = o, e.memoizedState = d;
			}
			px = null;
		}
		function ja(e, t) {
			if (typeof e != "function") throw Error("Invalid argument passed as callback. Expected a function. Instead received: " + e);
			e.call(t);
		}
		function Ma(e, t) {
			var n = e.shared.hiddenCallbacks;
			if (n !== null) for (e.shared.hiddenCallbacks = null, e = 0; e < n.length; e++) ja(n[e], t);
		}
		function Na(e, t) {
			var n = e.callbacks;
			if (n !== null) for (e.callbacks = null, e = 0; e < n.length; e++) ja(n[e], t);
		}
		function Pa(e, t) {
			var n = kC;
			xe(gx, n, e), xe(hx, t, e), kC = n | t.baseLanes;
		}
		function Fa(e) {
			xe(gx, kC, e), xe(hx, hx.current, e);
		}
		function Ia(e) {
			kC = gx.current, be(hx, e), be(gx, e);
		}
		function La(e) {
			var t = e.alternate;
			xe(xx, xx.current & yx, e), xe(_x, e, e), vx === null && (t === null || hx.current !== null || t.memoizedState !== null) && (vx = e);
		}
		function Ra(e) {
			xe(xx, xx.current, e), xe(_x, e, e), vx === null && (vx = e);
		}
		function za(e) {
			e.tag === 22 ? (xe(xx, xx.current, e), xe(_x, e, e), vx === null && (vx = e)) : Ba(e);
		}
		function Ba(e) {
			xe(xx, xx.current, e), xe(_x, _x.current, e);
		}
		function Va(e) {
			be(_x, e), vx === e && (vx = null), be(xx, e);
		}
		function Ha(e, t) {
			xe(_x, _x.current, e), xe(xx, t, e);
		}
		function Ua(e) {
			be(xx, e), be(_x, e), vx === e && (vx = null);
		}
		function Wa(e) {
			for (var t = e; t !== null;) {
				if (t.tag === 13) {
					var n = t.memoizedState;
					if (n !== null && (n = n.dehydrated, n === null || I(n) || ip(n))) return t;
				} else if (t.tag === 19 && t.memoizedProps.revealOrder !== "independent") {
					if (t.flags & 128) return t;
				} else if (t.child !== null) {
					t.child.return = t, t = t.child;
					continue;
				}
				if (t === e) break;
				for (; t.sibling === null;) {
					if (t.return === null || t.return === e) return null;
					t = t.return;
				}
				t.sibling.return = t.return, t = t.sibling;
			}
			return null;
		}
		function D() {
			var e = X;
			Ux === null ? Ux = [e] : Ux.push(e);
		}
		function O() {
			var e = X;
			if (Ux !== null && (Wx++, Ux[Wx] !== e)) {
				var t = S(Y);
				if (!Ox.has(t) && (Ox.add(t), Ux !== null)) {
					for (var n = "", r = 0; r <= Wx; r++) {
						var i = Ux[r], a = r === Wx ? e : i;
						for (i = r + 1 + ". " + i; 30 > i.length;) i += " ";
						i += a + "\n", n += i;
					}
					console.error("React has detected a change in the order of Hooks called by %s. This will lead to bugs and errors if not fixed. For more information, read the Rules of Hooks: https://react.dev/link/rules-of-hooks\n\n   Previous render            Next render\n   ------------------------------------------------------\n%s   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^\n", t, n);
				}
			}
		}
		function Ga(e) {
			e == null || Jm(e) || console.error("%s received a final argument that is not an array (instead, received `%s`). When specified, the final argument must be an array.", X, typeof e);
		}
		function Ka() {
			var e = S(Y);
			jx.has(e) || (jx.add(e), console.error("ReactDOM.useFormState has been renamed to React.useActionState. Please update %s to use React.useActionState.", e));
		}
		function qa() {
			throw Error("Invalid hook call. Hooks can only be called inside of the body of a function component. This could happen for one of the following reasons:\n1. You might have mismatching versions of React and the renderer (such as React DOM)\n2. You might be breaking the Rules of Hooks\n3. You might have more than one copy of React in the same app\nSee https://react.dev/link/invalid-hook-call for tips about how to debug and fix this problem.");
		}
		function Ja(e, t) {
			if (Gx) return !1;
			if (t === null) return console.error("%s received a final argument during this render, but not during the previous render. Even though the final argument is optional, its type cannot change between renders.", X), !1;
			e.length !== t.length && console.error("The final argument passed to %s changed size between renders. The order and size of this array must remain constant.\n\nPrevious: %s\nIncoming: %s", X, "[" + t.join(", ") + "]", "[" + e.join(", ") + "]");
			for (var n = 0; n < t.length && n < e.length; n++) if (!K_(e[n], t[n])) return !1;
			return !0;
		}
		function Ya(e, t, n, r, i, a) {
			Mx = a, Y = t, Ux = e === null ? null : e._debugHookTypes, Wx = -1, Gx = e !== null && e.type !== t.type, (Object.prototype.toString.call(n) === "[object AsyncFunction]" || Object.prototype.toString.call(n) === "[object AsyncGeneratorFunction]") && (a = S(Y), Ax.has(a) || (Ax.add(a), console.error("%s is an async Client Component. Only Server Components can be async at the moment. This error is often caused by accidentally adding `'use client'` to a module that was originally written for the server.", a === null ? "An unknown Component" : "<" + a + ">"))), t.memoizedState = null, t.updateQueue = null, t.lanes = 0, R.H = e !== null && e.memoizedState !== null ? Yx : Ux === null ? qx : Jx, Lx = a = (t.mode & Hv) !== U;
			var o = Eb(n, r, i);
			if (Lx = !1, Ix && (o = Za(t, n, r, i)), a) {
				Ke(!0);
				try {
					o = Za(t, n, r, i);
				} finally {
					Ke(!1);
				}
			}
			return Xa(e, t), o;
		}
		function Xa(e, t) {
			t._debugHookTypes = Ux, t.dependencies === null ? Bx !== null && (t.dependencies = {
				lanes: 0,
				firstContext: null,
				_debugThenableState: Bx
			}) : t.dependencies._debugThenableState = Bx;
			var n = Bx;
			if (qb !== null && ua(qb, t) && (n !== null || Jb === null || Yb || (Yb = !0, console.error(Jb)), Jb = qb = null), R.H = Kx, n = Nx !== null && Nx.next !== null, Mx = 0, Ux = X = Px = Nx = Y = null, Wx = -1, e !== null && (e.flags & 1206910976) != (t.flags & 1206910976) && console.error("Internal React error: Expected static flag was missing. Please notify the React team."), Fx = !1, zx = 0, Bx = null, n) throw Error("Rendered fewer hooks than expected. This may be caused by an accidental early return statement.");
			e === null || hS || (e = e.dependencies, e !== null && Ci(e) && (hS = !0)), Zb ? (Zb = !1, e = !0) : e = !1, e && (t = S(t) || "Unknown", kx.has(t) || Ax.has(t) || (kx.add(t), console.error("`use` was called from inside a try/catch block. This is not allowed and can lead to unexpected behavior. To handle errors triggered by `use`, wrap your component in a error boundary.")));
		}
		function Za(e, t, n, r) {
			Y = e;
			var i = 0;
			do {
				if (Ix && (Bx = null), zx = 0, Ix = !1, i >= Hx) throw Error("Too many re-renders. React limits the number of renders to prevent an infinite loop.");
				if (i += 1, Gx = !1, Px = Nx = null, e.updateQueue != null) {
					var a = e.updateQueue;
					a.lastEffect = null, a.events = null, a.stores = null, a.memoCache != null && (a.memoCache.index = 0);
				}
				Wx = -1, R.H = Xx, a = Eb(t, n, r);
			} while (Ix);
			return a;
		}
		function Qa() {
			var e = R.H, t = e.useState()[0];
			return t = typeof t.then == "function" ? io(t) : t, e = e.useState()[0], (Nx === null ? null : Nx.memoizedState) !== e && (Y.flags |= 1024), t;
		}
		function $a() {
			var e = Rx !== 0;
			return Rx = 0, e;
		}
		function eo(e, t, n) {
			t.updateQueue = e.updateQueue, t.flags = (t.mode & Uv) === U ? t.flags & -2053 : t.flags & -805308421, e.lanes &= ~n;
		}
		function to(e) {
			if (Fx) {
				for (e = e.memoizedState; e !== null;) {
					var t = e.queue;
					t !== null && (t.pending = null), e = e.next;
				}
				Fx = !1;
			}
			Mx = 0, Ux = Px = Nx = Y = null, Wx = -1, X = null, Ix = !1, zx = Rx = 0, Bx = null;
		}
		function no() {
			var e = {
				memoizedState: null,
				baseState: null,
				baseQueue: null,
				queue: null,
				next: null
			};
			return Px === null ? Y.memoizedState = Px = e : Px = Px.next = e, Px;
		}
		function k() {
			if (Nx === null) {
				var e = Y.alternate;
				e = e === null ? null : e.memoizedState;
			} else e = Nx.next;
			var t = Px === null ? Y.memoizedState : Px.next;
			if (t !== null) Px = t, Nx = e;
			else {
				if (e === null) throw Y.alternate === null ? Error("Update hook called on initial render. This is likely a bug in React. Please file an issue.") : Error("Rendered more hooks than during the previous render.");
				Nx = e, e = {
					memoizedState: Nx.memoizedState,
					baseState: Nx.baseState,
					baseQueue: Nx.baseQueue,
					queue: Nx.queue,
					next: null
				}, Px === null ? Y.memoizedState = Px = e : Px = Px.next = e;
			}
			return Px;
		}
		function ro() {
			return {
				lastEffect: null,
				events: null,
				stores: null,
				memoCache: null
			};
		}
		function io(e) {
			var t = zx;
			return zx += 1, Bx === null && (Bx = ia()), e = oa(Bx, e, t, Y), t = Y, (Px === null ? t.memoizedState : Px.next) === null && (t = t.alternate, R.H = t !== null && t.memoizedState !== null ? Yx : qx), e;
		}
		function ao(e) {
			if (typeof e == "object" && e) {
				if (typeof e.then == "function") return io(e);
				if (e.$$typeof === Gm) return;
				if (e.$$typeof === Fm) return Ti(e);
			}
			throw Error("An unsupported type was passed to use(): " + String(e));
		}
		function oo(e) {
			var t = null, n = Y.updateQueue;
			if (n !== null && (t = n.memoCache), t == null) {
				var r = Y.alternate;
				r !== null && (r = r.updateQueue, r !== null && (r = r.memoCache, r != null && (t = {
					data: r.data.map(function(e) {
						return e.slice();
					}),
					index: 0
				})));
			}
			if (t ??= {
				data: [],
				index: 0
			}, n === null && (n = ro(), Y.updateQueue = n), n.memoCache = t, n = t.data[t.index], n === void 0 || Gx) for (n = t.data[t.index] = Array(e), r = 0; r < e; r++) n[r] = Um;
			else n.length !== e && console.error("Expected a constant size argument for each invocation of useMemoCache. The previous cache was allocated with size %s but size %s was requested.", n.length, e);
			return t.index++, n;
		}
		function so(e, t) {
			return typeof t == "function" ? t(e) : t;
		}
		function co(e, t, n) {
			var r = no();
			if (n !== void 0) {
				var i = n(t);
				if (Lx) {
					Ke(!0);
					try {
						n(t);
					} finally {
						Ke(!1);
					}
				}
			} else i = t;
			return r.memoizedState = r.baseState = i, e = {
				pending: null,
				lanes: 0,
				dispatch: null,
				lastRenderedReducer: e,
				lastRenderedState: i
			}, r.queue = e, e = e.dispatch = gs.bind(null, Y, e), [r.memoizedState, e];
		}
		function lo(e) {
			return uo(k(), Nx, e);
		}
		function uo(e, t, n) {
			var r = e.queue;
			if (r === null) throw Error("Should have a queue. You are likely calling Hooks conditionally, which is not allowed. (https://react.dev/link/invalid-hook-call)");
			r.lastRenderedReducer = n;
			var i = e.baseQueue, a = r.pending;
			if (a !== null) {
				if (i !== null) {
					var o = i.next;
					i.next = a.next, a.next = o;
				}
				t.baseQueue !== i && console.error("Internal error: Expected work-in-progress queue to be a clone. This is a bug in React."), t.baseQueue = i = a, r.pending = null;
			}
			if (a = e.baseState, i === null) e.memoizedState = a;
			else {
				t = i.next;
				var s = o = null, c = null, l = t, u = !1;
				do {
					var d = l.lane & -536870913;
					if (d === l.lane ? (Mx & d) === d : ($ & d) === d) {
						var f = l.revertLane;
						if (f === 0) c !== null && (c = c.next = {
							lane: 0,
							revertLane: 0,
							gesture: null,
							action: l.action,
							hasEagerState: l.hasEagerState,
							eagerState: l.eagerState,
							next: null
						}), d === ub && (u = !0);
						else if ((Mx & f) === f) {
							l = l.next, f === ub && (u = !0);
							continue;
						} else d = {
							lane: 0,
							revertLane: l.revertLane,
							gesture: null,
							action: l.action,
							hasEagerState: l.hasEagerState,
							eagerState: l.eagerState,
							next: null
						}, c === null ? (s = c = d, o = a) : c = c.next = d, Y.lanes |= f, jC |= f;
						d = l.action, Lx && n(a, d), a = l.hasEagerState ? l.eagerState : n(a, d);
					} else f = {
						lane: d,
						revertLane: l.revertLane,
						gesture: l.gesture,
						action: l.action,
						hasEagerState: l.hasEagerState,
						eagerState: l.eagerState,
						next: null
					}, c === null ? (s = c = f, o = a) : c = c.next = f, Y.lanes |= d, jC |= d;
					l = l.next;
				} while (l !== null && l !== t);
				if (c === null ? o = a : c.next = s, !K_(a, e.memoizedState) && (hS = !0, u && (n = db, n !== null))) throw n;
				e.memoizedState = a, e.baseState = o, e.baseQueue = c, r.lastRenderedState = a;
			}
			return i === null && (r.lanes = 0), [e.memoizedState, r.dispatch];
		}
		function fo(e) {
			var t = k(), n = t.queue;
			if (n === null) throw Error("Should have a queue. You are likely calling Hooks conditionally, which is not allowed. (https://react.dev/link/invalid-hook-call)");
			n.lastRenderedReducer = e;
			var r = n.dispatch, i = n.pending, a = t.memoizedState;
			if (i !== null) {
				n.pending = null;
				var o = i = i.next;
				do
					a = e(a, o.action), o = o.next;
				while (o !== i);
				K_(a, t.memoizedState) || (hS = !0), t.memoizedState = a, t.baseQueue === null && (t.baseState = a), n.lastRenderedState = a;
			}
			return [a, r];
		}
		function po(e, t, n) {
			var r = Y, i = no();
			if (G) {
				if (n === void 0) throw Error("Missing getServerSnapshot, which is required for server-rendered content. Will revert to client rendering.");
				var a = n();
				Dx || a === n() || (console.error("The result of getServerSnapshot should be cached to avoid an infinite loop"), Dx = !0);
			} else {
				if (a = t(), Dx || (n = t(), K_(a, n) || (console.error("The result of getSnapshot should be cached to avoid an infinite loop"), Dx = !0)), pC === null) throw Error("Expected a work-in-progress root. This is a bug in React. Please file an issue.");
				$ & 127 || ho(r, t, a);
			}
			return i.memoizedState = a, n = {
				value: a,
				getSnapshot: t
			}, i.queue = n, Ho(_o.bind(null, r, n, e), [e]), r.flags |= 2048, Ro(Cx | Ex, { destroy: void 0 }, go.bind(null, r, n, a, t), null), a;
		}
		function mo(e, t, n) {
			var r = Y, i = k(), a = G;
			if (a) {
				if (n === void 0) throw Error("Missing getServerSnapshot, which is required for server-rendered content. Will revert to client rendering.");
				n = n();
			} else if (n = t(), !Dx) {
				var o = t();
				K_(n, o) || (console.error("The result of getSnapshot should be cached to avoid an infinite loop"), Dx = !0);
			}
			if ((o = !K_((Nx || i).memoizedState, n)) && (i.memoizedState = n, hS = !0), i = i.queue, Vo(2048, Ex, _o.bind(null, r, i, e), [e]), e = i.getSnapshot !== t || o || Px !== null && (Px.memoizedState.tag & Cx) !== Sx, Ro(e ? Cx | Ex : Ex, { destroy: void 0 }, go.bind(null, r, i, n, t), null), e) {
				if (r.flags |= 2048, pC === null) throw Error("Expected a work-in-progress root. This is a bug in React. Please file an issue.");
				a || Mx & 127 || ho(r, t, n);
			}
			return n;
		}
		function ho(e, t, n) {
			e.flags |= 16384, e = {
				getSnapshot: t,
				value: n
			}, t = Y.updateQueue, t === null ? (t = ro(), Y.updateQueue = t, t.stores = [e]) : (n = t.stores, n === null ? t.stores = [e] : n.push(e));
		}
		function go(e, t, n, r) {
			t.value = n, t.getSnapshot = r, vo(t) && yo(e);
		}
		function _o(e, t, n) {
			return n(function() {
				vo(t) && (Ni(2, "updateSyncExternalStore()", e), yo(e));
			});
		}
		function vo(e) {
			var t = e.getSnapshot;
			e = e.value;
			try {
				var n = t();
				return !K_(e, n);
			} catch {
				return !0;
			}
		}
		function yo(e) {
			var t = Fr(e, 2);
			t !== null && su(t, e, 2);
		}
		function bo(e) {
			var t = no();
			if (typeof e == "function") {
				var n = e;
				if (e = n(), Lx) {
					Ke(!0);
					try {
						n();
					} finally {
						Ke(!1);
					}
				}
			}
			return t.memoizedState = t.baseState = e, t.queue = {
				pending: null,
				lanes: 0,
				dispatch: null,
				lastRenderedReducer: so,
				lastRenderedState: e
			}, t;
		}
		function xo(e) {
			e = bo(e);
			var t = e.queue, n = _s.bind(null, Y, t);
			return t.dispatch = n, [e.memoizedState, n];
		}
		function So(e) {
			var t = no();
			t.memoizedState = t.baseState = e;
			var n = {
				pending: null,
				lanes: 0,
				dispatch: null,
				lastRenderedReducer: null,
				lastRenderedState: null
			};
			return t.queue = n, t = ys.bind(null, Y, !0, n), n.dispatch = t, [e, t];
		}
		function Co(e, t) {
			return wo(k(), Nx, e, t);
		}
		function wo(e, t, n, r) {
			return e.baseState = n, uo(e, Nx, typeof r == "function" ? r : so);
		}
		function To(e, t) {
			var n = k();
			return Nx === null ? (n.baseState = e, [e, n.queue.dispatch]) : wo(n, Nx, e, t);
		}
		function Eo(e, t, n, r, i) {
			if (bs(e)) throw Error("Cannot update action state while rendering.");
			if (e = t.action, e !== null) {
				var a = {
					payload: i,
					action: e,
					next: null,
					isTransition: !0,
					status: "pending",
					value: null,
					reason: null,
					listeners: [],
					then: function(e) {
						a.listeners.push(e);
					}
				};
				R.T === null ? a.isTransition = !1 : n(!0), r(a), n = t.pending, n === null ? (a.next = t.pending = a, Do(t, a)) : (a.next = n.next, t.pending = n.next = a);
			}
		}
		function Do(e, t) {
			var n = t.action, r = t.payload, i = e.state;
			if (t.isTransition) {
				var a = R.T, o = {};
				o.types = a === null ? null : a.types, o._updatedFibers = /* @__PURE__ */ new Set(), R.T = o;
				try {
					var s = n(i, r), c = R.S;
					c !== null && c(o, s), Oo(e, t, s);
				} catch (n) {
					Ao(e, t, n);
				} finally {
					a !== null && o.types !== null && (a.types !== null && a.types !== o.types && console.error("We expected inner Transitions to have transferred the outer types set and that you cannot add to the outer Transition while inside the inner.This is a bug in React."), a.types = o.types), R.T = a, a === null && o._updatedFibers && (e = o._updatedFibers.size, o._updatedFibers.clear(), 10 < e && console.warn("Detected a large number of updates inside startTransition. If this is due to a subscription please re-write it to use React provided hooks. Otherwise concurrent mode guarantees are off the table."));
				}
			} else try {
				o = n(i, r), Oo(e, t, o);
			} catch (n) {
				Ao(e, t, n);
			}
		}
		function Oo(e, t, n) {
			typeof n == "object" && n && typeof n.then == "function" ? (R.asyncTransitions++, n.then(is, is), n.then(function(n) {
				ko(e, t, n);
			}, function(n) {
				return Ao(e, t, n);
			}), t.isTransition || console.error("An async function with useActionState was called outside of a transition. This is likely not what you intended (for example, isPending will not update correctly). Either call the returned function inside startTransition, or pass it to an `action` or `formAction` prop.")) : ko(e, t, n);
		}
		function ko(e, t, n) {
			t.status = "fulfilled", t.value = n, jo(t), e.state = n, t = e.pending, t !== null && (n = t.next, n === t ? e.pending = null : (n = n.next, t.next = n, Do(e, n)));
		}
		function Ao(e, t, n) {
			var r = e.pending;
			if (e.pending = null, r !== null) {
				r = r.next;
				do
					t.status = "rejected", t.reason = n, jo(t), t = t.next;
				while (t !== r);
			}
			e.action = null;
		}
		function jo(e) {
			e = e.listeners;
			for (var t = 0; t < e.length; t++) (0, e[t])();
		}
		function Mo(e, t) {
			return t;
		}
		function No(e, t) {
			if (G) {
				var n = pC.formState;
				if (n !== null) {
					a: {
						var r = Y;
						if (G) {
							if (ry) {
								b: {
									for (var i = ry, a = sy; i.nodeType !== 8;) if (!a || (i = op(i.nextSibling), i === null)) {
										i = null;
										break b;
									}
									a = i.data, i = a === pT || a === mT ? i : null;
								}
								if (i) {
									ry = op(i.nextSibling), r = i.data === pT;
									break a;
								}
							}
							li(r);
						}
						r = !1;
					}
					r && (t = n[0]);
				}
			}
			return n = no(), n.memoizedState = n.baseState = t, r = {
				pending: null,
				lanes: 0,
				dispatch: null,
				lastRenderedReducer: Mo,
				lastRenderedState: t
			}, n.queue = r, n = _s.bind(null, Y, r), r.dispatch = n, r = bo(!1), a = ys.bind(null, Y, !1, r.queue), r = no(), i = {
				state: t,
				dispatch: null,
				action: e,
				pending: null
			}, r.queue = i, n = Eo.bind(null, Y, i, a, n), i.dispatch = n, r.memoizedState = e, [
				t,
				n,
				!1
			];
		}
		function Po(e) {
			return Fo(k(), Nx, e);
		}
		function Fo(e, t, n) {
			if (t = uo(e, t, Mo)[0], e = lo(so)[0], typeof t == "object" && t && typeof t.then == "function") try {
				var r = io(t);
			} catch (e) {
				throw e === Ub ? Gb : e;
			}
			else r = t;
			t = k();
			var i = t.queue, a = i.dispatch;
			return n !== t.memoizedState && (Y.flags |= 2048, Ro(Cx | Ex, { destroy: void 0 }, Io.bind(null, i, n), null)), [
				r,
				a,
				e
			];
		}
		function Io(e, t) {
			e.action = t;
		}
		function Lo(e) {
			var t = k(), n = Nx;
			if (n !== null) return Fo(t, n, e);
			k(), t = t.memoizedState, n = k();
			var r = n.queue.dispatch;
			return n.memoizedState = e, [
				t,
				r,
				!1
			];
		}
		function Ro(e, t, n, r) {
			return e = {
				tag: e,
				create: n,
				deps: r,
				inst: t,
				next: null
			}, t = Y.updateQueue, t === null && (t = ro(), Y.updateQueue = t), n = t.lastEffect, n === null ? t.lastEffect = e.next = e : (r = n.next, n.next = e, e.next = r, t.lastEffect = e), e;
		}
		function zo(e) {
			var t = no();
			return e = { current: e }, t.memoizedState = e;
		}
		function Bo(e, t, n, r) {
			var i = no();
			Y.flags |= e, i.memoizedState = Ro(Cx | t, { destroy: void 0 }, n, r === void 0 ? null : r);
		}
		function Vo(e, t, n, r) {
			var i = k();
			r = r === void 0 ? null : r;
			var a = i.memoizedState.inst;
			Nx !== null && r !== null && Ja(r, Nx.memoizedState.deps) ? i.memoizedState = Ro(t, a, n, r) : (Y.flags |= e, i.memoizedState = Ro(Cx | t, a, n, r));
		}
		function Ho(e, t) {
			(Y.mode & Uv) === U ? Bo(8390656, Ex, e, t) : Bo(545261568, Ex, e, t);
		}
		function Uo(e) {
			Y.flags |= 4;
			var t = Y.updateQueue;
			if (t === null) t = ro(), Y.updateQueue = t, t.events = [e];
			else {
				var n = t.events;
				n === null ? t.events = [e] : n.push(e);
			}
		}
		function Wo(e) {
			var t = no(), n = { impl: e };
			return t.memoizedState = n, function() {
				if ((Z & iC) !== rC) throw Error("A function wrapped in useEffectEvent can't be called during rendering.");
				return n.impl.apply(void 0, arguments);
			};
		}
		function Go(e) {
			var t = k().memoizedState;
			return Uo({
				ref: t,
				nextImpl: e
			}), function() {
				if ((Z & iC) !== rC) throw Error("A function wrapped in useEffectEvent can't be called during rendering.");
				return t.impl.apply(void 0, arguments);
			};
		}
		function Ko(e, t) {
			var n = 4194308;
			return (Y.mode & Uv) !== U && (n |= 268435456), Bo(n, Tx, e, t);
		}
		function qo(e, t) {
			if (typeof t == "function") {
				e = e();
				var n = t(e);
				return function() {
					typeof n == "function" ? n() : t(null);
				};
			}
			if (t != null) return t.hasOwnProperty("current") || console.error("Expected useImperativeHandle() first argument to either be a ref callback or React.createRef() object. Instead received: %s.", "an object with keys {" + Object.keys(t).join(", ") + "}"), e = e(), t.current = e, function() {
				t.current = null;
			};
		}
		function Jo(e, t, n) {
			typeof t != "function" && console.error("Expected useImperativeHandle() second argument to be a function that creates a handle. Instead received: %s.", t === null ? "null" : typeof t), n = n == null ? null : n.concat([e]);
			var r = 4194308;
			(Y.mode & Uv) !== U && (r |= 268435456), Bo(r, Tx, qo.bind(null, t, e), n);
		}
		function Yo(e, t, n) {
			typeof t != "function" && console.error("Expected useImperativeHandle() second argument to be a function that creates a handle. Instead received: %s.", t === null ? "null" : typeof t), n = n == null ? null : n.concat([e]), Vo(4, Tx, qo.bind(null, t, e), n);
		}
		function Xo(e, t) {
			return no().memoizedState = [e, t === void 0 ? null : t], e;
		}
		function Zo(e, t) {
			var n = k();
			t = t === void 0 ? null : t;
			var r = n.memoizedState;
			return t !== null && Ja(t, r[1]) ? r[0] : (n.memoizedState = [e, t], e);
		}
		function Qo(e, t) {
			var n = no();
			t = t === void 0 ? null : t;
			var r = e();
			if (Lx) {
				Ke(!0);
				try {
					e();
				} finally {
					Ke(!1);
				}
			}
			return n.memoizedState = [r, t], r;
		}
		function $o(e, t) {
			var n = k();
			t = t === void 0 ? null : t;
			var r = n.memoizedState;
			if (t !== null && Ja(t, r[1])) return r[0];
			if (r = e(), Lx) {
				Ke(!0);
				try {
					e();
				} finally {
					Ke(!1);
				}
			}
			return n.memoizedState = [r, t], r;
		}
		function A(e, t) {
			return ns(no(), e, t);
		}
		function es(e, t) {
			return rs(k(), Nx.memoizedState, e, t);
		}
		function ts(e, t) {
			var n = k();
			return Nx === null ? ns(n, e, t) : rs(n, Nx.memoizedState, e, t);
		}
		function ns(e, t, n) {
			return n === void 0 || Mx & 1073741824 && !($ & 261930) ? e.memoizedState = t : (e.memoizedState = n, e = au(), Y.lanes |= e, jC |= e, n);
		}
		function rs(e, t, n, r) {
			return K_(n, t) ? n : hx.current === null ? !(Mx & 106) || Mx & 1073741824 && !($ & 261930) ? (hS = !0, e.memoizedState = n) : (e = au(), Y.lanes |= e, jC |= e, t) : (e = ns(e, n, r), K_(e, t) || (hS = !0), e);
		}
		function is() {
			R.asyncTransitions--;
		}
		function as(e, t, n, r, i) {
			var a = z.p;
			z.p = a !== 0 && a < Hh ? a : Hh;
			var o = R.T, s = {};
			s.types = o === null ? null : o.types, s._updatedFibers = /* @__PURE__ */ new Set(), R.T = s, ys(e, !1, t, n);
			try {
				var c = i(), l = R.S;
				if (l !== null && l(s, c), typeof c == "object" && c && typeof c.then == "function") {
					R.asyncTransitions++, c.then(is, is);
					var u = ea(c, r);
					vs(e, t, u, iu(e));
				} else vs(e, t, r, iu(e));
			} catch (n) {
				vs(e, t, {
					then: function() {},
					status: "rejected",
					reason: n
				}, iu(e));
			} finally {
				z.p = a, o !== null && s.types !== null && (o.types !== null && o.types !== s.types && console.error("We expected inner Transitions to have transferred the outer types set and that you cannot add to the outer Transition while inside the inner.This is a bug in React."), o.types = s.types), R.T = o, o === null && s._updatedFibers && (e = s._updatedFibers.size, s._updatedFibers.clear(), 10 < e && console.warn("Detected a large number of updates inside startTransition. If this is due to a subscription please re-write it to use React provided hooks. Otherwise concurrent mode guarantees are off the table."));
			}
		}
		function os(e, t, n, r) {
			if (e.tag !== 5) throw Error("Expected the form instance to be a HostComponent. This is a bug in React.");
			var i = ss(e).queue;
			Pi(e), as(e, i, t, QT, n === null ? p : function() {
				return cs(e), n(r);
			});
		}
		function ss(e) {
			var t = e.memoizedState;
			if (t !== null) return t;
			t = {
				memoizedState: QT,
				baseState: QT,
				baseQueue: null,
				queue: {
					pending: null,
					lanes: 0,
					dispatch: null,
					lastRenderedReducer: so,
					lastRenderedState: QT
				},
				next: null
			};
			var n = {};
			return t.next = {
				memoizedState: n,
				baseState: n,
				baseQueue: null,
				queue: {
					pending: null,
					lanes: 0,
					dispatch: null,
					lastRenderedReducer: so,
					lastRenderedState: n
				},
				next: null
			}, e.memoizedState = t, e = e.alternate, e !== null && (e.memoizedState = t), t;
		}
		function cs(e) {
			R.T === null && console.error("requestFormReset was called outside a transition or action. To fix, move to an action, or wrap with startTransition.");
			var t = ss(e);
			t.next === null && (t = e.alternate.memoizedState), vs(e, t.next.queue, {}, iu(e));
		}
		function ls() {
			var e = bo(!1);
			return e = as.bind(null, Y, e.queue, !0, !1), no().memoizedState = e, [!1, e];
		}
		function us() {
			var e = lo(so)[0], t = k().memoizedState;
			return [typeof e == "boolean" ? e : io(e), t];
		}
		function ds() {
			var e = fo(so)[0], t = k().memoizedState;
			return [typeof e == "boolean" ? e : io(e), t];
		}
		function fs() {
			return Ti($T);
		}
		function ps() {
			var e = no(), t = pC.identifierPrefix;
			if (G) {
				var n = ty, r = ey;
				n = (r & ~(1 << 32 - Fh(r) - 1)).toString(32) + n, t = "_" + t + "R_" + n, n = Rx++, 0 < n && (t += "H" + n.toString(32)), t += "_";
			} else n = Vx++, t = "_" + t + "r_" + n.toString(32) + "_";
			return e.memoizedState = t;
		}
		function ms() {
			return no().memoizedState = hs.bind(null, Y);
		}
		function hs(e, t) {
			for (var n = e.return; n !== null;) {
				switch (n.tag) {
					case 24:
					case 3:
						var r = iu(n), i = Ta(r), a = Ea(n, i, r);
						a !== null && (Ni(r, "refresh()", e), su(a, n, r), Da(a, n, r)), e = Oi(), t != null && a !== null && console.error("The seed argument is not enabled outside experimental channels."), i.payload = { cache: e };
						return;
				}
				n = n.return;
			}
		}
		function gs(e, t, n) {
			var r = arguments;
			typeof r[3] == "function" && console.error("State updates from the useState() and useReducer() Hooks don't support the second callback argument. To execute a side effect after rendering, declare it in the component body with useEffect()."), r = iu(e);
			var i = {
				lane: r,
				revertLane: 0,
				gesture: null,
				action: n,
				hasEagerState: !1,
				eagerState: null,
				next: null
			};
			bs(e) ? xs(t, i) : (i = Pr(e, t, i, r), i !== null && (Ni(r, "dispatch()", e), su(i, e, r), Ss(i, t, r)));
		}
		function _s(e, t, n) {
			var r = arguments;
			typeof r[3] == "function" && console.error("State updates from the useState() and useReducer() Hooks don't support the second callback argument. To execute a side effect after rendering, declare it in the component body with useEffect()."), r = iu(e), vs(e, t, n, r) && Ni(r, "setState()", e);
		}
		function vs(e, t, n, r) {
			var i = {
				lane: r,
				revertLane: 0,
				gesture: null,
				action: n,
				hasEagerState: !1,
				eagerState: null,
				next: null
			};
			if (bs(e)) xs(t, i);
			else {
				var a = e.alternate;
				if (e.lanes === 0 && (a === null || a.lanes === 0) && (a = t.lastRenderedReducer, a !== null)) {
					var o = R.H;
					R.H = Qx;
					try {
						var s = t.lastRenderedState, c = a(s, n);
						if (i.hasEagerState = !0, i.eagerState = c, K_(c, s)) return Nr(e, t, i, 0), pC === null && Mr(), !1;
					} catch {} finally {
						R.H = o;
					}
				}
				if (n = Pr(e, t, i, r), n !== null) return su(n, e, r), Ss(n, t, r), !0;
			}
			return !1;
		}
		function ys(e, t, n, r) {
			if (R.T === null && ub === 0 && console.error("An optimistic state update occurred outside a transition or action. To fix, move the update to an action, or wrap with startTransition."), r = {
				lane: 2,
				revertLane: md(),
				gesture: null,
				action: r,
				hasEagerState: !1,
				eagerState: null,
				next: null
			}, bs(e)) {
				if (t) throw Error("Cannot update optimistic state while rendering.");
				console.error("Cannot call startTransition while rendering.");
			} else t = Pr(e, n, r, 2), t !== null && (Ni(2, "setOptimistic()", e), su(t, e, 2));
		}
		function bs(e) {
			var t = e.alternate;
			return e === Y || t !== null && t === Y;
		}
		function xs(e, t) {
			Ix = Fx = !0;
			var n = e.pending;
			n === null ? t.next = t : (t.next = n.next, n.next = t), e.pending = t;
		}
		function Ss(e, t, n) {
			if (n & 4194048) {
				var r = t.lanes;
				r &= e.pendingLanes, n |= r, t.lanes = n, it(e, n);
			}
		}
		function Cs(e) {
			if (e !== null && typeof e != "function") {
				var t = String(e);
				uS.has(t) || (uS.add(t), console.error("Expected the last optional `callback` argument to be a function. Instead received: %s.", e));
			}
		}
		function ws(e, t, n, r) {
			var i = e.memoizedState, a = n(r, i);
			if (e.mode & Hv) {
				Ke(!0);
				try {
					a = n(r, i);
				} finally {
					Ke(!1);
				}
			}
			a === void 0 && (t = _e(t) || "Component", oS.has(t) || (oS.add(t), console.error("%s.getDerivedStateFromProps(): A valid state object (or null) must be returned. You have returned undefined.", t))), i = a == null ? i : L({}, i, a), e.memoizedState = i, e.lanes === 0 && (e.updateQueue.baseState = i);
		}
		function Ts(e, t, n, r, i, a, o) {
			var s = e.stateNode;
			if (typeof s.shouldComponentUpdate == "function") {
				if (n = s.shouldComponentUpdate(r, a, o), e.mode & Hv) {
					Ke(!0);
					try {
						n = s.shouldComponentUpdate(r, a, o);
					} finally {
						Ke(!1);
					}
				}
				return n === void 0 && console.error("%s.shouldComponentUpdate(): Returned undefined instead of a boolean value. Make sure to return true or false.", _e(t) || "Component"), n;
			}
			return t.prototype && t.prototype.isPureReactComponent ? !er(n, r) || !er(i, a) : !0;
		}
		function Es(e, t, n, r) {
			var i = t.state;
			typeof t.componentWillReceiveProps == "function" && t.componentWillReceiveProps(n, r), typeof t.UNSAFE_componentWillReceiveProps == "function" && t.UNSAFE_componentWillReceiveProps(n, r), t.state !== i && (e = S(e) || "Component", tS.has(e) || (tS.add(e), console.error("%s.componentWillReceiveProps(): Assigning directly to this.state is deprecated (except inside a component's constructor). Use setState instead.", e)), dS.enqueueReplaceState(t, t.state, null));
		}
		function Ds(e, t) {
			var n = t;
			if ("ref" in t) for (var r in n = {}, t) r !== "ref" && (n[r] = t[r]);
			if (e = e.defaultProps) for (var i in n === t && (n = L({}, n)), e) n[i] === void 0 && (n[i] = e[i]);
			return n;
		}
		function Os(e) {
			hv(e), console.warn("%s\n\n%s\n", fS ? "An error occurred in the <" + fS + "> component." : "An error occurred in one of your React components.", "Consider adding an error boundary to your tree to customize error handling behavior.\nVisit https://react.dev/link/error-boundaries to learn more about error boundaries.");
		}
		function ks(e) {
			var t = fS ? "The above error occurred in the <" + fS + "> component." : "The above error occurred in one of your React components.", n = "React will try to recreate this component tree from scratch using the error boundary you provided, " + ((pS || "Anonymous") + ".");
			if (typeof e == "object" && e && typeof e.environmentName == "string") {
				var r = e.environmentName;
				e = [
					"%o\n\n%s\n\n%s\n",
					e,
					t,
					n
				].slice(0), typeof e[0] == "string" ? e.splice(0, 1, eE + " " + e[0], tE, rE + r + rE, nE) : e.splice(0, 0, eE, tE, rE + r + rE, nE), e.unshift(console), r = iE.apply(console.error, e), r();
			} else console.error("%o\n\n%s\n\n%s\n", e, t, n);
		}
		function As(e) {
			hv(e);
		}
		function js(e, t) {
			try {
				fS = t.source ? S(t.source) : null, pS = null;
				var n = t.value;
				if (R.actQueue !== null) R.thrownErrors.push(n);
				else {
					var r = e.onUncaughtError;
					r(n, { componentStack: t.stack });
				}
			} catch (e) {
				setTimeout(function() {
					throw e;
				});
			}
		}
		function Ms(e, t, n) {
			try {
				fS = n.source ? S(n.source) : null, pS = S(t);
				var r = e.onCaughtError;
				r(n.value, {
					componentStack: n.stack,
					errorBoundary: t.tag === 1 ? t.stateNode : null
				});
			} catch (e) {
				setTimeout(function() {
					throw e;
				});
			}
		}
		function Ns(e, t, n) {
			return n = Ta(n), n.tag = ux, n.payload = { element: null }, n.callback = function() {
				w(t.source, js, e, t);
			}, n;
		}
		function Ps(e) {
			return e = Ta(e), e.tag = ux, e;
		}
		function Fs(e, t, n, r) {
			var i = n.type.getDerivedStateFromError;
			if (typeof i == "function") {
				var a = r.value;
				e.payload = function() {
					return i(a);
				}, e.callback = function() {
					Br(n), w(r.source, Ms, t, n, r);
				};
			}
			var o = n.stateNode;
			o !== null && typeof o.componentDidCatch == "function" && (e.callback = function() {
				Br(n), w(r.source, Ms, t, n, r), typeof i != "function" && (KC === null ? KC = /* @__PURE__ */ new Set([this]) : KC.add(this)), Pb(this, r), typeof i == "function" || !(n.lanes & 2) && console.error("%s: Error boundaries should implement getDerivedStateFromError(). In that method, return a state update to display an error message or fallback UI.", S(n) || "Unknown");
			});
		}
		function Is(e, t, n, r, i) {
			if (n.flags |= 32768, Ph && nd(e, i), typeof r == "object" && r && typeof r.then == "function") {
				if (t = n.alternate, t !== null && Si(t, n, i, !0), G && (iy = !0), n = _x.current, n !== null) {
					switch (n.tag) {
						case 31:
						case 13:
						case 19: return vx === null ? xu() : n.alternate === null && AC === oC && (AC = lC), n.flags &= -257, n.flags |= 65536, n.lanes = i, r === Kb ? n.flags |= 16384 : (t = n.updateQueue, t === null ? n.updateQueue = /* @__PURE__ */ new Set([r]) : t.add(r), qu(e, r, i)), !1;
						case 22: return n.flags |= 65536, r === Kb ? n.flags |= 16384 : (t = n.updateQueue, t === null ? (t = {
							transitions: null,
							markerInstances: null,
							retryQueue: /* @__PURE__ */ new Set([r])
						}, n.updateQueue = t) : (n = t.retryQueue, n === null ? t.retryQueue = /* @__PURE__ */ new Set([r]) : n.add(r)), qu(e, r, i)), !1;
					}
					throw Error("Unexpected Suspense handler tag (" + n.tag + "). This is a bug in React.");
				}
				return qu(e, r, i), xu(), !1;
			}
			if (G) return iy = !0, t = _x.current, t === null ? (r !== cy && hi(Qr(Error("There was an error while hydrating but React was able to recover by instead client rendering the entire root.", { cause: r }), n)), e = e.current.alternate, e.flags |= 65536, i &= -i, e.lanes |= i, r = Qr(r, n), i = Ns(e.stateNode, r, i), Oa(e, i), AC !== uC && (AC = cC)) : (t.tag === 19 && console.error("SuspenseList should never catch while hydrating. This is a bug in React."), !(t.flags & 65536) && (t.flags |= 256), t.flags |= 65536, t.lanes = i, r !== cy && hi(Qr(Error("There was an error while hydrating but React was able to recover by instead client rendering from the nearest Suspense boundary.", { cause: r }), n))), !1;
			var a = Qr(Error("There was an error during concurrent rendering but React was able to recover by instead synchronously rendering the entire root.", { cause: r }), n);
			if (IC === null ? IC = [a] : IC.push(a), AC !== uC && (AC = cC), t === null) return !0;
			r = Qr(r, n), n = t;
			do {
				switch (n.tag) {
					case 3: return n.flags |= 65536, e = i & -i, n.lanes |= e, e = Ns(n.stateNode, r, e), Oa(n, e), !1;
					case 1:
						if (t = n.type, a = n.stateNode, !(n.flags & 128) && (typeof t.getDerivedStateFromError == "function" || a !== null && typeof a.componentDidCatch == "function" && (KC === null || !KC.has(a)))) return n.flags |= 65536, i &= -i, n.lanes |= i, i = Ps(i), Fs(i, e, n, r), Oa(n, i), !1;
						break;
					case 22: if (n.memoizedState !== null) return n.flags |= 65536, !1;
				}
				n = n.return;
			} while (n !== null);
			return !1;
		}
		function Ls(e, t, n, r) {
			t.child = e === null ? ox(t, null, n, r) : ax(t, e.child, n, r);
		}
		function Rs(e, t, n, r, i) {
			n = n.render;
			var a = Rr(n);
			if (a !== n && (n = a, e !== null && (hS = !0)), a = t.ref, "ref" in r) {
				var o = {};
				for (var s in r) s !== "ref" && (o[s] = r[s]);
			} else o = r;
			return wi(t), r = Ya(e, t, n, o, a, i), s = $a(), e !== null && !hS ? (eo(e, t, i), fc(e, t, i)) : (G && s && ti(t), t.flags |= 1, Ls(e, t, r, i), t.child);
		}
		function zs(e, t, n, r, i) {
			if (e === null) {
				var a = n.type;
				return typeof a == "function" && !Ur(a) && a.defaultProps === void 0 && n.compare === null ? (n = Rr(a), t.tag = 15, t.type = n, Qs(t, a), Bs(e, t, n, r, i)) : (e = Kr(n.type, null, r, t, t.mode, i), e.ref = t.ref, e.return = t, t.child = e);
			}
			if (a = e.child, !pc(e, i)) {
				var o = a.memoizedProps;
				if (n = n.compare, n = n === null ? er : n, n(o, r) && e.ref === t.ref) return fc(e, t, i);
			}
			return t.flags |= 1, e = Wr(a, r), e.ref = t.ref, e.return = t, t.child = e;
		}
		function Bs(e, t, n, r, i) {
			if (e !== null) {
				var a = e.memoizedProps;
				if (er(a, r) && e.ref === t.ref && t.type === e.type) {
					if (hS = !1, t.pendingProps = r = a, pc(e, i)) e.flags & 131072 && (hS = !0);
					else return t.lanes = e.lanes, fc(e, t, i);
				}
			}
			return Js(e, t, n, r, i);
		}
		function Vs(e, t, n, r) {
			var i = r.children, a = e === null ? null : e.memoizedState;
			if (e === null && t.stateNode === null && (t.stateNode = {
				_visibility: Nv,
				_pendingMarkers: null,
				_retryCache: null,
				_transitions: null
			}), r.mode === "hidden") {
				if (t.flags & 128) {
					if (a = a === null ? n : a.baseLanes | n, e !== null) {
						for (r = t.child = e.child, i = 0; r !== null;) i = i | r.lanes | r.childLanes, r = r.sibling;
						r = i & ~a;
					} else r = 0, t.child = null;
					return Us(e, t, a, n, r);
				}
				if (n & 536870912) t.memoizedState = {
					baseLanes: 0,
					cachePool: null
				}, e !== null && na(t, a === null ? null : a.cachePool), a === null ? Fa(t) : Pa(t, a), za(t);
				else return r = t.lanes = 536870912, Us(e, t, a === null ? n : a.baseLanes | n, n, r);
			} else a === null ? (e !== null && na(t, null), Fa(t), Ba(t)) : (na(t, a.cachePool), Pa(t, a), Ba(t), t.memoizedState = null);
			return Ls(e, t, i, n), t.child;
		}
		function Hs(e, t) {
			return e !== null && e.tag === 22 || t.stateNode !== null || (t.stateNode = {
				_visibility: Nv,
				_pendingMarkers: null,
				_retryCache: null,
				_transitions: null
			}), t.sibling;
		}
		function Us(e, t, n, r, i) {
			var a = ta();
			return a = a === null ? null : {
				parent: vy._currentValue,
				pool: a
			}, t.memoizedState = {
				baseLanes: n,
				cachePool: a
			}, e !== null && na(t, null), Fa(t), za(t), e !== null && Si(e, t, r, !0), t.childLanes = i, null;
		}
		function Ws(e, t) {
			var n = t.hidden;
			return n !== void 0 && console.error("<Activity> doesn't accept a hidden prop. Use mode=\"hidden\" instead.\n- <Activity %s>\n+ <Activity %s>", !0 === n ? "hidden" : !1 === n ? "hidden={false}" : "hidden={...}", n ? "mode=\"hidden\"" : "mode=\"visible\""), t = rc({
				mode: t.mode,
				children: t.children
			}, e.mode), t.ref = e.ref, e.child = t, t.return = e, t;
		}
		function Gs(e, t, n) {
			return ax(t, e.child, null, n), e = Ws(t, t.pendingProps), e.flags |= 2, Va(t), t.memoizedState = null, e;
		}
		function Ks(e, t, n) {
			var r = t.pendingProps, i = !!(t.flags & 128);
			if (t.flags &= -129, e === null) {
				if (G) {
					if (r.mode === "hidden") return e = Ws(t, r), t.lanes = 536870912, e.memoizedState = {
						baseLanes: 0,
						cachePool: null
					}, Hs(null, e);
					if (Ra(t), (e = ry) ? (n = rp(e, sy), n = n !== null && n.data === rT ? n : null, n !== null && (r = {
						dehydrated: n,
						treeContext: ri(),
						retryLane: 536870912,
						hydrationErrors: null
					}, t.memoizedState = r, r = Xr(n), r.return = t, t.child = r, ny = t, ry = null)) : n = null, n === null) throw ci(t, e), li(t);
					return t.lanes = 536870912, null;
				}
				return Ws(t, r);
			}
			var a = e.memoizedState;
			if (a !== null) {
				var o = a.dehydrated;
				if (Ra(t), i) {
					if (t.flags & 256) t.flags &= -257, t = Gs(e, t, n);
					else if (t.memoizedState !== null) t.child = e.child, t.flags |= 128, t = null;
					else throw Error("Client rendering an Activity suspended it again. This is a bug in React.");
				} else if (si(), n & 536870912 && bu(t), hS || Si(e, t, n, !1), i = (n & e.childLanes) !== 0, hS || i) {
					if (hx.current === null) {
						if (r = pC, r !== null && (o = at(r, n), o !== 0 && o !== a.retryLane)) throw a.retryLane = o, Fr(e, o), su(r, e, o), mS;
						xu();
					}
					t = Gs(e, t, n);
				} else e = a.treeContext, ry = op(o.nextSibling), ny = t, G = !0, oy = null, iy = !1, ay = null, sy = !1, e !== null && ii(t, e), t = Ws(t, r), t.flags |= 134221824;
				return t;
			}
			return a = e.child, r = {
				mode: r.mode,
				children: r.children
			}, n & 536870912 && (n & e.lanes) !== 0 && bu(t), e = Wr(a, r), e.ref = t.ref, t.child = e, e.return = t, e;
		}
		function qs(e, t) {
			var n = t.ref;
			if (n === null) e !== null && e.ref !== null && (t.flags |= 4194816);
			else {
				if (typeof n != "function" && typeof n != "object") throw Error("Expected ref to be a function, an object returned by React.createRef(), or undefined/null.");
				(e === null || e.ref !== n) && (t.flags |= 4194816);
			}
		}
		function Js(e, t, n, r, i) {
			if (n.prototype && typeof n.prototype.render == "function") {
				var a = _e(n) || "Unknown";
				gS[a] || (console.error("The <%s /> component appears to have a render method, but doesn't extend React.Component. This is likely to cause errors. Change %s to extend React.Component instead.", a, a), gS[a] = !0);
			}
			return t.mode & Hv && mb.recordLegacyContextWarning(t, null), e === null && (Qs(t, t.type), n.contextTypes && (a = _e(n) || "Unknown", vS[a] || (vS[a] = !0, console.error("%s uses the legacy contextTypes API which was removed in React 19. Use React.createContext() with React.useContext() instead. (https://react.dev/link/legacy-context)", a)))), wi(t), n = Ya(e, t, n, r, void 0, i), r = $a(), e !== null && !hS ? (eo(e, t, i), fc(e, t, i)) : (G && r && ti(t), t.flags |= 1, Ls(e, t, n, i), t.child);
		}
		function Ys(e, t, n, r, i, a) {
			return wi(t), Wx = -1, Gx = e !== null && e.type !== t.type, t.updateQueue = null, n = Za(t, r, n, i), Xa(e, t), r = $a(), e !== null && !hS ? (eo(e, t, a), fc(e, t, a)) : (G && r && ti(t), t.flags |= 1, Ls(e, t, n, a), t.child);
		}
		function Xs(e, t, n, r, i) {
			switch (u(t)) {
				case !1:
					var a = t.stateNode, o = new t.type(t.memoizedProps, a.context).state;
					a.updater.enqueueSetState(a, o, null);
					break;
				case !0:
					t.flags |= 128, t.flags |= 65536, a = Error("Simulated error coming from DevTools");
					var s = i & -i;
					if (t.lanes |= s, o = pC, o === null) throw Error("Expected a work-in-progress root. This is a bug in React. Please file an issue.");
					s = Ps(s), Fs(s, o, t, Qr(a, t)), Oa(t, s);
			}
			if (wi(t), t.stateNode === null) {
				if (o = Rv, a = n.contextType, "contextType" in n && a !== null && (a === void 0 || a.$$typeof !== Fm) && !lS.has(n) && (lS.add(n), s = a === void 0 ? " However, it is set to undefined. This can be caused by a typo or by mixing up named and default imports. This can also happen due to a circular dependency, so try moving the createContext() call to a separate file." : typeof a == "object" ? a.$$typeof === Pm ? " Did you accidentally pass the Context.Consumer instead?" : " However, it is set to an object with keys {" + Object.keys(a).join(", ") + "}." : " However, it is set to a " + typeof a + ".", console.error("%s defines an invalid contextType. contextType should point to the Context object returned by React.createContext().%s", _e(n) || "Component", s)), typeof a == "object" && a && (o = Ti(a)), a = new n(r, o), t.mode & Hv) {
					Ke(!0);
					try {
						a = new n(r, o);
					} finally {
						Ke(!1);
					}
				}
				if (o = t.memoizedState = a.state !== null && a.state !== void 0 ? a.state : null, a.updater = dS, t.stateNode = a, a._reactInternals = t, a._reactInternalInstance = eS, typeof n.getDerivedStateFromProps == "function" && o === null && (o = _e(n) || "Component", nS.has(o) || (nS.add(o), console.error("`%s` uses `getDerivedStateFromProps` but its initial state is %s. This is not recommended. Instead, define the initial state by assigning an object to `this.state` in the constructor of `%s`. This ensures that `getDerivedStateFromProps` arguments have a consistent shape.", o, a.state === null ? "null" : "undefined", o))), typeof n.getDerivedStateFromProps == "function" || typeof a.getSnapshotBeforeUpdate == "function") {
					var c = s = o = null;
					if (typeof a.componentWillMount == "function" && !0 !== a.componentWillMount.__suppressDeprecationWarning ? o = "componentWillMount" : typeof a.UNSAFE_componentWillMount == "function" && (o = "UNSAFE_componentWillMount"), typeof a.componentWillReceiveProps == "function" && !0 !== a.componentWillReceiveProps.__suppressDeprecationWarning ? s = "componentWillReceiveProps" : typeof a.UNSAFE_componentWillReceiveProps == "function" && (s = "UNSAFE_componentWillReceiveProps"), typeof a.componentWillUpdate == "function" && !0 !== a.componentWillUpdate.__suppressDeprecationWarning ? c = "componentWillUpdate" : typeof a.UNSAFE_componentWillUpdate == "function" && (c = "UNSAFE_componentWillUpdate"), o !== null || s !== null || c !== null) {
						a = _e(n) || "Component";
						var l = typeof n.getDerivedStateFromProps == "function" ? "getDerivedStateFromProps()" : "getSnapshotBeforeUpdate()";
						iS.has(a) || (iS.add(a), console.error("Unsafe legacy lifecycles will not be called for components using new component APIs.\n\n%s uses %s but also contains the following legacy lifecycles:%s%s%s\n\nThe above lifecycles should be removed. Learn more about this warning here:\nhttps://react.dev/link/unsafe-component-lifecycles", a, l, o === null ? "" : "\n  " + o, s === null ? "" : "\n  " + s, c === null ? "" : "\n  " + c));
					}
				}
				a = t.stateNode, o = _e(n) || "Component", a.render || (n.prototype && typeof n.prototype.render == "function" ? console.error("No `render` method found on the %s instance: did you accidentally return an object from the constructor?", o) : console.error("No `render` method found on the %s instance: you may have forgotten to define `render`.", o)), !a.getInitialState || a.getInitialState.isReactClassApproved || a.state || console.error("getInitialState was defined on %s, a plain JavaScript class. This is only supported for classes created using React.createClass. Did you mean to define a state property instead?", o), a.getDefaultProps && !a.getDefaultProps.isReactClassApproved && console.error("getDefaultProps was defined on %s, a plain JavaScript class. This is only supported for classes created using React.createClass. Use a static property to define defaultProps instead.", o), a.contextType && console.error("contextType was defined as an instance property on %s. Use a static property to define contextType instead.", o), n.childContextTypes && !cS.has(n) && (cS.add(n), console.error("%s uses the legacy childContextTypes API which was removed in React 19. Use React.createContext() instead. (https://react.dev/link/legacy-context)", o)), n.contextTypes && !sS.has(n) && (sS.add(n), console.error("%s uses the legacy contextTypes API which was removed in React 19. Use React.createContext() with static contextType instead. (https://react.dev/link/legacy-context)", o)), typeof a.componentShouldUpdate == "function" && console.error("%s has a method called componentShouldUpdate(). Did you mean shouldComponentUpdate()? The name is phrased as a question because the function is expected to return a value.", o), n.prototype && n.prototype.isPureReactComponent && a.shouldComponentUpdate !== void 0 && console.error("%s has a method called shouldComponentUpdate(). shouldComponentUpdate should not be used when extending React.PureComponent. Please extend React.Component if shouldComponentUpdate is used.", _e(n) || "A pure component"), typeof a.componentDidUnmount == "function" && console.error("%s has a method called componentDidUnmount(). But there is no such lifecycle method. Did you mean componentWillUnmount()?", o), typeof a.componentDidReceiveProps == "function" && console.error("%s has a method called componentDidReceiveProps(). But there is no such lifecycle method. If you meant to update the state in response to changing props, use componentWillReceiveProps(). If you meant to fetch data or run side-effects or mutations after React has updated the UI, use componentDidUpdate().", o), typeof a.componentWillRecieveProps == "function" && console.error("%s has a method called componentWillRecieveProps(). Did you mean componentWillReceiveProps()?", o), typeof a.UNSAFE_componentWillRecieveProps == "function" && console.error("%s has a method called UNSAFE_componentWillRecieveProps(). Did you mean UNSAFE_componentWillReceiveProps()?", o), s = a.props !== r, a.props !== void 0 && s && console.error("When calling super() in `%s`, make sure to pass up the same props that your component's constructor was passed.", o), a.defaultProps && console.error("Setting defaultProps as an instance property on %s is not supported and will be ignored. Instead, define defaultProps as a static property on %s.", o, o), typeof a.getSnapshotBeforeUpdate != "function" || typeof a.componentDidUpdate == "function" || rS.has(n) || (rS.add(n), console.error("%s: getSnapshotBeforeUpdate() should be used with componentDidUpdate(). This component defines getSnapshotBeforeUpdate() only.", _e(n))), typeof a.getDerivedStateFromProps == "function" && console.error("%s: getDerivedStateFromProps() is defined as an instance method and will be ignored. Instead, declare it as a static method.", o), typeof a.getDerivedStateFromError == "function" && console.error("%s: getDerivedStateFromError() is defined as an instance method and will be ignored. Instead, declare it as a static method.", o), typeof n.getSnapshotBeforeUpdate == "function" && console.error("%s: getSnapshotBeforeUpdate() is defined as a static method and will be ignored. Instead, declare it as an instance method.", o), (s = a.state) && (typeof s != "object" || Jm(s)) && console.error("%s.state: must be set to an object or null", o), typeof a.getChildContext == "function" && typeof n.childContextTypes != "object" && console.error("%s.getChildContext(): childContextTypes must be defined in order to use getChildContext().", o), a = t.stateNode, a.props = r, a.state = t.memoizedState, a.refs = {}, Ca(t), o = n.contextType, a.context = typeof o == "object" && o ? Ti(o) : Rv, a.state === r && (o = _e(n) || "Component", aS.has(o) || (aS.add(o), console.error("%s: It is not recommended to assign props directly to state because updates to props won't be reflected in state. In most cases, it is better to use props directly.", o))), t.mode & Hv && mb.recordLegacyContextWarning(t, a), mb.recordUnsafeLifecycleWarnings(t, a), a.state = t.memoizedState, o = n.getDerivedStateFromProps, typeof o == "function" && (ws(t, n, o, r), a.state = t.memoizedState), typeof n.getDerivedStateFromProps == "function" || typeof a.getSnapshotBeforeUpdate == "function" || typeof a.UNSAFE_componentWillMount != "function" && typeof a.componentWillMount != "function" || (o = a.state, typeof a.componentWillMount == "function" && a.componentWillMount(), typeof a.UNSAFE_componentWillMount == "function" && a.UNSAFE_componentWillMount(), o !== a.state && (console.error("%s.componentWillMount(): Assigning directly to this.state is deprecated (except inside a component's constructor). Use setState instead.", S(t) || "Component"), dS.enqueueReplaceState(a, a.state, null)), Aa(t, r, a, i), ka(), a.state = t.memoizedState), typeof a.componentDidMount == "function" && (t.flags |= 4194308), (t.mode & Uv) !== U && (t.flags |= 268435456), a = !0;
			} else if (e === null) {
				a = t.stateNode;
				var d = t.memoizedProps;
				s = Ds(n, d), a.props = s;
				var f = a.context;
				c = n.contextType, o = Rv, typeof c == "object" && c && (o = Ti(c)), l = n.getDerivedStateFromProps, c = typeof l == "function" || typeof a.getSnapshotBeforeUpdate == "function", d = t.pendingProps !== d, c || typeof a.UNSAFE_componentWillReceiveProps != "function" && typeof a.componentWillReceiveProps != "function" || (d || f !== o) && Es(t, a, r, o), dx = !1;
				var p = t.memoizedState;
				a.state = p, Aa(t, r, a, i), ka(), f = t.memoizedState, d || p !== f || dx ? (typeof l == "function" && (ws(t, n, l, r), f = t.memoizedState), (s = dx || Ts(t, n, s, r, p, f, o)) ? (c || typeof a.UNSAFE_componentWillMount != "function" && typeof a.componentWillMount != "function" || (typeof a.componentWillMount == "function" && a.componentWillMount(), typeof a.UNSAFE_componentWillMount == "function" && a.UNSAFE_componentWillMount()), typeof a.componentDidMount == "function" && (t.flags |= 4194308), (t.mode & Uv) !== U && (t.flags |= 268435456)) : (typeof a.componentDidMount == "function" && (t.flags |= 4194308), (t.mode & Uv) !== U && (t.flags |= 268435456), t.memoizedProps = r, t.memoizedState = f), a.props = r, a.state = f, a.context = o, a = s) : (typeof a.componentDidMount == "function" && (t.flags |= 4194308), (t.mode & Uv) !== U && (t.flags |= 268435456), a = !1);
			} else {
				a = t.stateNode, wa(e, t), o = t.memoizedProps, c = Ds(n, o), a.props = c, l = t.pendingProps, p = a.context, f = n.contextType, s = Rv, typeof f == "object" && f && (s = Ti(f)), d = n.getDerivedStateFromProps, (f = typeof d == "function" || typeof a.getSnapshotBeforeUpdate == "function") || typeof a.UNSAFE_componentWillReceiveProps != "function" && typeof a.componentWillReceiveProps != "function" || (o !== l || p !== s) && Es(t, a, r, s), dx = !1, p = t.memoizedState, a.state = p, Aa(t, r, a, i), ka();
				var m = t.memoizedState;
				o !== l || p !== m || dx || e !== null && e.dependencies !== null && Ci(e.dependencies) ? (typeof d == "function" && (ws(t, n, d, r), m = t.memoizedState), (c = dx || Ts(t, n, c, r, p, m, s) || e !== null && e.dependencies !== null && Ci(e.dependencies)) ? (f || typeof a.UNSAFE_componentWillUpdate != "function" && typeof a.componentWillUpdate != "function" || (typeof a.componentWillUpdate == "function" && a.componentWillUpdate(r, m, s), typeof a.UNSAFE_componentWillUpdate == "function" && a.UNSAFE_componentWillUpdate(r, m, s)), typeof a.componentDidUpdate == "function" && (t.flags |= 4), typeof a.getSnapshotBeforeUpdate == "function" && (t.flags |= 1024)) : (typeof a.componentDidUpdate != "function" || o === e.memoizedProps && p === e.memoizedState || (t.flags |= 4), typeof a.getSnapshotBeforeUpdate != "function" || o === e.memoizedProps && p === e.memoizedState || (t.flags |= 1024), t.memoizedProps = r, t.memoizedState = m), a.props = r, a.state = m, a.context = s, a = c) : (typeof a.componentDidUpdate != "function" || o === e.memoizedProps && p === e.memoizedState || (t.flags |= 4), typeof a.getSnapshotBeforeUpdate != "function" || o === e.memoizedProps && p === e.memoizedState || (t.flags |= 1024), a = !1);
			}
			if (s = a, qs(e, t), o = !!(t.flags & 128), s || o) {
				if (s = t.stateNode, Re(t), o && typeof n.getDerivedStateFromError != "function") n = null, Oy = -1;
				else if (n = Ob(s), t.mode & Hv) {
					Ke(!0);
					try {
						Ob(s);
					} finally {
						Ke(!1);
					}
				}
				t.flags |= 1, e !== null && o ? (t.child = ax(t, e.child, null, i), t.child = ax(t, null, n, i)) : Ls(e, t, n, i), t.memoizedState = s.state, e = t.child;
			} else e = fc(e, t, i);
			return i = t.stateNode, a && i.props !== r && (bS || console.error("It looks like %s is reassigning its own `this.props` while rendering. This is not supported and can lead to confusing bugs.", S(t) || "a component"), bS = !0), e;
		}
		function Zs(e, t, n, r) {
			return pi(), t.flags |= 256, Ls(e, t, n, r), t.child;
		}
		function Qs(e, t) {
			t && t.childContextTypes && console.error("childContextTypes cannot be defined on a function component.\n  %s.childContextTypes = ...", t.displayName || t.name || "Component"), typeof t.getDerivedStateFromProps == "function" && (e = _e(t) || "Unknown", yS[e] || (console.error("%s: Function components do not support getDerivedStateFromProps.", e), yS[e] = !0)), typeof t.contextType == "object" && t.contextType !== null && (t = _e(t) || "Unknown", _S[t] || (console.error("%s: Function components do not support contextType.", t), _S[t] = !0));
		}
		function $s(e) {
			return {
				baseLanes: e,
				cachePool: ra()
			};
		}
		function ec(e, t, n) {
			return e = e === null ? 0 : e.childLanes & ~n, t && (e |= PC), e;
		}
		function tc(e, t, n) {
			var r = t.pendingProps;
			c(t) && (t.flags |= 128);
			var i = !1, a = !!(t.flags & 128), o;
			if ((o = a) || (o = e !== null && e.memoizedState === null ? !1 : (xx.current & bx) !== 0), o && (i = !0, t.flags &= -129), o = !!(t.flags & 32), t.flags &= -33, e === null) {
				if (G) {
					if (i ? La(t) : Ba(t), (e = ry) ? (n = rp(e, sy), n = n !== null && n.data !== rT ? n : null, n !== null && (o = {
						dehydrated: n,
						treeContext: ri(),
						retryLane: 536870912,
						hydrationErrors: null
					}, t.memoizedState = o, o = Xr(n), o.return = t, t.child = o, ny = t, ry = null)) : n = null, n === null) throw ci(t, e), li(t);
					return t.lanes = ip(n) ? 32 : 536870912, null;
				}
				return a = r.children, r = r.fallback, i ? (Ba(t), i = t.mode, a = rc({
					mode: "hidden",
					children: a
				}, i), r = Jr(r, i, n, null), a.return = t, r.return = t, a.sibling = r, t.child = a, r = t.child, r.memoizedState = $s(n), r.childLanes = ec(e, o, n), t.memoizedState = wS, Hs(null, r)) : (La(t), nc(t, a));
			}
			var s = e.memoizedState;
			if (s !== null) {
				var l = s.dehydrated;
				if (l !== null) return ac(e, t, a, o, r, l, s, n);
			}
			return i ? (Ba(t), i = r.fallback, a = t.mode, s = e.child, l = s.sibling, r = Wr(s, {
				mode: "hidden",
				children: r.children
			}), r.subtreeFlags = s.subtreeFlags & 1206910976, l === null ? (i = Jr(i, a, n, null), i.flags |= 2) : i = Wr(l, i), i.return = t, r.return = t, r.sibling = i, t.child = r, Hs(null, r), r = t.child, i = e.child.memoizedState, i === null ? i = $s(n) : (a = i.cachePool, a === null ? a = ra() : (s = vy._currentValue, a = a.parent === s ? a : {
				parent: s,
				pool: s
			}), i = {
				baseLanes: i.baseLanes | n,
				cachePool: a
			}), r.memoizedState = i, r.childLanes = ec(e, o, n), t.memoizedState = wS, Hs(e.child, r)) : (s !== null && (n & 62914560) === n && (n & e.lanes) !== 0 && bu(t), La(t), n = e.child, e = n.sibling, n = Wr(n, {
				mode: "visible",
				children: r.children
			}), n.return = t, n.sibling = null, e !== null && (o = t.deletions, o === null ? (t.deletions = [e], t.flags |= 16) : o.push(e)), t.child = n, t.memoizedState = null, n);
		}
		function nc(e, t) {
			return t = rc({
				mode: "visible",
				children: t
			}, e.mode), t.return = e, e.child = t;
		}
		function rc(e, t) {
			return e = g(22, e, null, t), e.lanes = 0, e;
		}
		function ic(e, t, n) {
			return ax(t, e.child, null, n), e = nc(t, t.pendingProps.children), e.flags |= 2, t.memoizedState = null, e;
		}
		function ac(e, t, n, r, i, a, o, s) {
			if (n) {
				if (t.flags & 256) return La(t), t.flags &= -257, ic(e, t, s);
				if (t.memoizedState !== null) return Ba(t), t.child = e.child, t.flags |= 128, null;
				Ba(t);
				var c = i.fallback, l = t.mode, u = rc({
					mode: "visible",
					children: i.children
				}, l);
				return c = Jr(c, l, s, null), c.flags |= 2, u.return = t, c.return = t, u.sibling = c, t.child = u, ax(t, e.child, null, s), c = t.child, c.memoizedState = $s(s), c.childLanes = ec(e, r, s), t.memoizedState = wS, Hs(null, c);
			}
			if (La(t), si(), s & 536870912 && bu(t), ip(a)) {
				if (r = a.nextSibling && a.nextSibling.dataset, r) {
					c = r.dgst;
					var d = r.msg;
					l = r.stck, u = r.cstck;
				}
				return a = d, o = c, i = l, r = u, c = o, l = a, u = i, i = r, c !== wb && (r = Error(l || "The server could not finish this Suspense boundary, likely due to an error during server rendering. Switched to client rendering."), r.stack = u || "", r.digest = c, c = i === void 0 ? null : i, l = {
					value: r,
					source: null,
					stack: c
				}, typeof c == "string" && Kv.set(r, l), hi(l)), ic(e, t, s);
			}
			if (hS || Si(e, t, s, !1), r = (s & e.childLanes) !== 0, hS || r) {
				if (hx.current !== null) return ic(e, t, s);
				if (r = pC, r !== null && (c = at(r, s), c !== 0 && c !== o.retryLane)) throw o.retryLane = c, Fr(e, c), su(r, e, c), mS;
				return I(a) || xu(), ic(e, t, s);
			}
			return I(a) ? (t.flags |= 192, t.child = e.child, null) : (e = o.treeContext, ry = op(a.nextSibling), ny = t, G = !0, oy = null, iy = !1, ay = null, sy = !1, e !== null && ii(t, e), t = nc(t, i.children), t.flags |= 134221824, t);
		}
		function oc(e, t, n) {
			e.lanes |= t;
			var r = e.alternate;
			r !== null && (r.lanes |= t), bi(e.return, t, n);
		}
		function sc(e) {
			for (var t = null; e !== null;) {
				var n = e.alternate;
				n !== null && Wa(n) === null && (t = e), e = e.sibling;
			}
			return t;
		}
		function cc(e, t, n, r, i, a) {
			var o = e.memoizedState;
			o === null ? e.memoizedState = {
				isBackwards: t,
				rendering: null,
				renderingStartTime: 0,
				last: r,
				tail: n,
				tailMode: i,
				treeForkCount: a
			} : (o.isBackwards = t, o.rendering = null, o.renderingStartTime = 0, o.last = r, o.tail = n, o.tailMode = i, o.treeForkCount = a);
		}
		function lc(e) {
			var t = e.child;
			for (e.child = null; t !== null;) {
				var n = t.sibling;
				t.sibling = e.child, e.child = t, t = n;
			}
		}
		function uc(e, t, n) {
			var r = t.pendingProps, i = r.revealOrder, a = r.tail, o = r.children, s = xx.current;
			if (t.flags & 128) return Ha(t, s), null;
			if ((r = (s & bx) !== 0) ? (s = s & yx | bx, t.flags |= 128) : s &= yx, Ha(t, s), s = i ?? "null", i != null && i !== "forwards" && i !== "backwards" && i !== "unstable_legacy-backwards" && i !== "together" && i !== "independent" && !xS[s]) {
				if (xS[s] = !0, typeof i == "string") switch (i.toLowerCase()) {
					case "together":
					case "forwards":
					case "backwards":
					case "independent":
						console.error("\"%s\" is not a valid value for revealOrder on <SuspenseList />. Use lowercase \"%s\" instead.", i, i.toLowerCase());
						break;
					case "forward":
					case "backward":
						console.error("\"%s\" is not a valid value for revealOrder on <SuspenseList />. React uses the -s suffix in the spelling. Use \"%ss\" instead.", i, i.toLowerCase());
						break;
					default: console.error("\"%s\" is not a supported revealOrder on <SuspenseList />. Did you mean \"independent\", \"together\", \"forwards\" or \"backwards\"?", i);
				}
				else console.error("%s is not a supported value for revealOrder on <SuspenseList />. Did you mean \"independent\", \"together\", \"forwards\" or \"backwards\"?", i);
			}
			s = a ?? "null", SS[s] || a == null || (a !== "visible" && a !== "collapsed" && a !== "hidden" ? (SS[s] = !0, console.error("\"%s\" is not a supported value for tail on <SuspenseList />. Did you mean \"visible\", \"collapsed\" or \"hidden\"?", a)) : i != null && i !== "forwards" && i !== "backwards" && i !== "unstable_legacy-backwards" && (SS[s] = !0, console.error("<SuspenseList tail=\"%s\" /> is only valid if revealOrder is \"forwards\" (default) or \"backwards\". Did you mean to specify revealOrder=\"forwards\"?", a)));
			a: if ((i == null || i === "forwards" || i === "backwards" || i === "unstable_legacy-backwards") && o != null && !1 !== o) {
				if (Jm(o)) {
					for (s = 0; s < o.length; s++) if (!Sa(o[s], s)) break a;
				} else if (s = ge(o), typeof s == "function") {
					if (s = s.call(o)) for (var c = s.next(), l = 0; !c.done; c = s.next()) {
						if (!Sa(c.value, l)) break a;
						l++;
					}
				} else console.error("A single row was passed to a <SuspenseList revealOrder=\"%s\" />. This is not useful since it needs multiple rows. Did you mean to pass multiple children or an array?", i);
			}
			if (i === "backwards" && e !== null ? (lc(e), Ls(e, t, o, n), lc(e)) : Ls(e, t, o, n), G ? (ai(), o = Xv) : o = 0, !r && e !== null && e.flags & 128) a: for (e = t.child; e !== null;) {
				if (e.tag === 13) e.memoizedState !== null && oc(e, n, t);
				else if (e.tag === 19) oc(e, n, t);
				else if (e.child !== null) {
					e.child.return = e, e = e.child;
					continue;
				}
				if (e === t) break a;
				for (; e.sibling === null;) {
					if (e.return === null || e.return === t) break a;
					e = e.return;
				}
				e.sibling.return = e.return, e = e.sibling;
			}
			switch (i) {
				case "backwards":
					n = sc(t.child), n === null ? (i = t.child, t.child = null) : (i = n.sibling, n.sibling = null, lc(t)), cc(t, !0, i, null, a, o);
					break;
				case "unstable_legacy-backwards":
					for (n = null, i = t.child, t.child = null; i !== null;) {
						if (e = i.alternate, e !== null && Wa(e) === null) {
							t.child = i;
							break;
						}
						e = i.sibling, i.sibling = n, n = i, i = e;
					}
					cc(t, !0, n, null, a, o);
					break;
				case "together":
					cc(t, !1, null, null, void 0, o);
					break;
				case "independent":
					t.memoizedState = null;
					break;
				default: n = sc(t.child), n === null ? (i = t.child, t.child = null) : (i = n.sibling, n.sibling = null), cc(t, !1, i, n, a, o);
			}
			return t.child;
		}
		function dc(e, t, n) {
			var r = t.type, i = t.pendingProps, a = i.value;
			return "value" in i || TS || (TS = !0, console.error("The `value` prop is required for the `<Context.Provider>`. Did you misspell it or forget to pass it?")), vi(t, r, a), Ls(e, t, i.children, n), t.child;
		}
		function fc(e, t, n) {
			if (e !== null && (t.dependencies = e.dependencies), Oy = -1, jC |= t.lanes, (n & t.childLanes) === 0) {
				if (e !== null) {
					if (Si(e, t, n, !1), (n & t.childLanes) === 0) return null;
				} else return null;
			}
			if (e !== null && t.child !== e.child) throw Error("Resuming work not yet implemented.");
			if (t.child !== null) {
				for (e = t.child, n = Wr(e, e.pendingProps), t.child = n, n.return = t; e.sibling !== null;) e = e.sibling, n = n.sibling = Wr(e, e.pendingProps), n.return = t;
				n.sibling = null;
			}
			return t.child;
		}
		function pc(e, t) {
			return (e.lanes & t) !== 0 || (e = e.dependencies, !!(e !== null && Ci(e)));
		}
		function mc(e, t, n) {
			switch (t.tag) {
				case 3:
					Ce(t, t.stateNode.containerInfo), vi(t, vy, e.memoizedState.cache), pi();
					break;
				case 27:
				case 5:
					Ee(t);
					break;
				case 4:
					Ce(t, t.stateNode.containerInfo);
					break;
				case 10:
					vi(t, t.type, t.memoizedProps.value);
					break;
				case 12:
					(n & t.childLanes) !== 0 && (t.flags |= 4), t.flags |= 2048;
					var r = t.stateNode;
					r.effectDuration = -0, r.passiveEffectDuration = -0;
					break;
				case 31:
					if (t.memoizedState !== null) return t.flags |= 128, Ra(t), null;
					break;
				case 13:
					if (r = t.memoizedState, r !== null) {
						if (r.dehydrated !== null) return La(t), t.flags |= 128, null;
						r = Si(e, t, n, !1);
						var i = t.child.childLanes;
						return r || (n & i) !== 0 ? tc(e, t, n) : (La(t), e = fc(e, t, n), e === null ? null : e.sibling);
					}
					La(t);
					break;
				case 19:
					if (t.flags & 128) return uc(e, t, n);
					if (i = !!(e.flags & 128), r = (n & t.childLanes) !== 0, r ||= (Si(e, t, n, !1), (n & t.childLanes) !== 0), i) {
						if (r) return uc(e, t, n);
						t.flags |= 128;
					}
					if (i = t.memoizedState, i !== null && (i.rendering = null, i.tail = null, i.lastEffect = null), Ha(t, xx.current), r) break;
					return null;
				case 22: return t.lanes = 0, Vs(e, t, n, t.pendingProps);
				case 24: vi(t, vy, e.memoizedState.cache);
			}
			return fc(e, t, n);
		}
		function hc(e, t, n) {
			if (t._debugNeedsRemount && e !== null) {
				n = Kr(Rr(t.elementType), t.key, t.pendingProps, t._debugOwner || null, t.mode, t.lanes), n._debugStack = t._debugStack, n._debugTask = t._debugTask;
				var r = t.return;
				if (r === null) throw Error("Cannot swap the root fiber.");
				if (e.alternate = null, t.alternate = null, n.index = t.index, n.sibling = t.sibling, n.return = t.return, n.ref = t.ref, n._debugInfo = t._debugInfo, t === r.child) r.child = n;
				else {
					var i = r.child;
					if (i === null) throw Error("Expected parent to have a child.");
					for (; i.sibling !== t;) if (i = i.sibling, i === null) throw Error("Expected to find the previous sibling.");
					i.sibling = n;
				}
				return t = r.deletions, t === null ? (r.deletions = [e], r.flags |= 16) : t.push(e), n.flags |= 134217730, n;
			}
			if (e !== null) {
				if (e.memoizedProps !== t.pendingProps || t.type !== e.type) hS = !0;
				else {
					if (!pc(e, n) && !(t.flags & 128)) return hS = !1, mc(e, t, n);
					hS = !!(e.flags & 131072);
				}
			} else hS = !1, (r = G) && (ai(), r = !!(t.flags & 1048576)), r && (r = t.index, ai(), ei(t, Xv, r));
			switch (t.lanes = 0, t.tag) {
				case 16:
					a: if (r = t.pendingProps, e = sa(t.elementType), e = Rr(e), t.type = e, typeof e == "function") Ur(e) ? (r = Ds(e, r), t.tag = 1, t = Xs(null, t, e, r, n)) : (t.tag = 0, Qs(t, e), t = Js(null, t, e, r, n));
					else {
						if (e != null) {
							if (i = e.$$typeof, i === Im) {
								t.tag = 11, t = Rs(null, t, e, r, n);
								break a;
							}
							if (i === zm) {
								t.tag = 14, t = zs(null, t, e, r, n);
								break a;
							}
							if (i === Fm) {
								t.tag = 10, t.type = e, t = dc(null, t, n);
								break a;
							}
						}
						throw t = "", typeof e == "object" && e && e.$$typeof === Bm && (t = " Did you wrap a component in React.lazy() more than once?"), n = _e(e) || e, Error("Element type is invalid. Received a promise that resolves to: " + n + ". Lazy element type must resolve to a class or function." + t);
					}
					return t;
				case 0: return Js(e, t, t.type, t.pendingProps, n);
				case 1: return r = t.type, i = Ds(r, t.pendingProps), Xs(e, t, r, i, n);
				case 3:
					a: {
						if (Ce(t, t.stateNode.containerInfo), e === null) throw Error("Should have a current fiber. This is a bug in React.");
						r = t.pendingProps;
						var a = t.memoizedState;
						i = a.element, wa(e, t), Aa(t, r, null, n);
						var o = t.memoizedState;
						if (r = o.cache, vi(t, vy, r), r !== a.cache && xi(t, [vy], n, !0), ka(), r = o.element, a.isDehydrated) {
							if (a = {
								element: r,
								isDehydrated: !1,
								cache: o.cache
							}, t.updateQueue.baseState = a, t.memoizedState = a, t.flags & 256) {
								t = Zs(e, t, r, n);
								break a;
							}
							if (r !== i) {
								i = Qr(Error("This root received an early update, before anything was able hydrate. Switched the entire root to client rendering."), t), hi(i), t = Zs(e, t, r, n);
								break a;
							}
							switch (e = t.stateNode.containerInfo, e.nodeType) {
								case 9:
									e = e.body;
									break;
								default: e = e.nodeName === "HTML" ? e.ownerDocument.body : e;
							}
							for (ry = op(e.firstChild), ny = t, G = !0, oy = null, iy = !1, ay = null, sy = !0, n = ox(t, null, r, n), t.child = n; n;) n.flags = n.flags & -3 | 134221824, n = n.sibling;
						} else {
							if (pi(), r === i) {
								t = fc(e, t, n);
								break a;
							}
							Ls(e, t, r, n);
						}
						t = t.child;
					}
					return t;
				case 26: return qs(e, t), e === null ? (n = Sp(t.type, null, t.pendingProps, null)) ? t.memoizedState = n : G || (t.stateNode = Qd(t.type, t.pendingProps, Se(th.current), t)) : t.memoizedState = Sp(t.type, e.memoizedProps, t.pendingProps, e.memoizedState), null;
				case 27: return Ee(t), e === null && G && (r = Se(th.current), i = Te(), r = t.stateNode = gp(t.type, t.pendingProps, r, i, !1), iy || (i = Gd(r, t.type, t.pendingProps, i), i !== null && (oi(t, 0).serverProps = i)), ny = t, sy = !0, i = ry, ff(t.type) ? (FT = i, ry = op(r.firstChild)) : ry = i), Ls(e, t, t.pendingProps.children, n), qs(e, t), e === null && (t.flags |= 4194304), t.child;
				case 5: return e === null && G && (a = Te(), r = fn(t.type, a.ancestorInfo), i = ry, (o = !i) || (o = tp(i, t.type, t.pendingProps, sy), o === null ? a = !1 : (t.stateNode = o, iy || (a = Gd(o, t.type, t.pendingProps, a), a !== null && (oi(t, 0).serverProps = a)), ny = t, ry = op(o.firstChild), sy = !1, a = !0), o = !a), o && (r && ci(t, i), li(t))), Ee(t), i = t.type, a = t.pendingProps, o = e === null ? null : e.memoizedProps, r = a.children, ef(i, a) ? r = null : o !== null && ef(i, o) && (t.flags |= 32), t.memoizedState !== null && (i = Ya(e, t, Qa, null, null, n), $T._currentValue = i), qs(e, t), Ls(e, t, r, n), t.child;
				case 6: return e === null && G && (n = t.pendingProps, e = Te(), r = e.ancestorInfo.current, n = r == null || pn(n, r.tag, e.ancestorInfo.implicitRootScope), e = ry, (r = !e) || (r = np(e, t.pendingProps, sy), r === null ? r = !1 : (t.stateNode = r, ny = t, ry = null, r = !0), r = !r), r && (n && ci(t, e), li(t))), null;
				case 13: return tc(e, t, n);
				case 4: return Ce(t, t.stateNode.containerInfo), r = t.pendingProps, e === null ? t.child = ax(t, null, r, n) : Ls(e, t, r, n), t.child;
				case 11: return Rs(e, t, t.type, t.pendingProps, n);
				case 7: return r = t.pendingProps, qs(e, t), Ls(e, t, r, n), t.child;
				case 8: return Ls(e, t, t.pendingProps.children, n), t.child;
				case 12: return t.flags |= 4, t.flags |= 2048, r = t.stateNode, r.effectDuration = -0, r.passiveEffectDuration = -0, Ls(e, t, t.pendingProps.children, n), t.child;
				case 10: return dc(e, t, n);
				case 9: return i = t.type._context, r = t.pendingProps.children, typeof r != "function" && console.error("A context consumer was rendered with multiple children, or a child that isn't a function. A context consumer expects a single child that is a function. If you did pass a function, make sure there is no trailing or leading whitespace around it."), wi(t), i = Ti(i), r = Eb(r, i, void 0), t.flags |= 1, Ls(e, t, r, n), t.child;
				case 14: return zs(e, t, t.type, t.pendingProps, n);
				case 15: return Bs(e, t, t.type, t.pendingProps, n);
				case 19: return uc(e, t, n);
				case 31: return Ks(e, t, n);
				case 22: return Vs(e, t, n, t.pendingProps);
				case 24: return wi(t), r = Ti(vy), e === null ? (i = ta(), i === null && (i = pC, a = Oi(), i.pooledCache = a, ki(a), a !== null && (i.pooledCacheLanes |= n), i = a), t.memoizedState = {
					parent: r,
					cache: i
				}, Ca(t), vi(t, vy, i)) : ((e.lanes & n) !== 0 && (wa(e, t), Aa(t, null, null, n), ka()), i = e.memoizedState, a = t.memoizedState, i.parent === r ? (r = a.cache, vi(t, vy, r), r !== i.cache && xi(t, [vy], n, !0)) : (i = {
					parent: r,
					cache: r
				}, t.memoizedState = i, t.lanes === 0 && (t.memoizedState = t.updateQueue.baseState = i), vi(t, vy, r))), Ls(e, t, t.pendingProps.children, n), t.child;
				case 30: return t.stateNode === null && (t.stateNode = {
					autoName: null,
					paired: null,
					clones: null,
					ref: null
				}), r = t.pendingProps, r.name != null && r.name !== "auto" ? t.flags |= e === null ? 18882560 : 18874368 : G && ti(t), r.className !== void 0 && (i = typeof r.className == "string" ? JSON.stringify(r.className) : "{...}", CS[i] || (CS[i] = !0, console.error("<ViewTransition> doesn't accept a \"className\" prop. It has been renamed to \"default\".\n-   <ViewTransition className=%s>\n+   <ViewTransition default=%s>", i, i))), e !== null && e.memoizedProps.name !== r.name ? t.flags |= 4194816 : qs(e, t), Ls(e, t, r.children, n), t.child;
				case 29: throw t.pendingProps;
			}
			throw Error("Unknown unit of work tag (" + t.tag + "). This error is likely caused by a bug in React. Please file an issue.");
		}
		function gc(e) {
			e.flags |= 4;
		}
		function _c(e, t, n, r, i) {
			var a;
			if ((a = (e.mode & Wv) !== U) && (a = n === null ? Lp(t, r) : Lp(t, r) && (r.src !== n.src || r.srcSet !== n.srcSet)), a) {
				if (e.flags |= 16777216, (i & 335544128) === i) {
					if (e.stateNode.complete) e.flags |= 8192;
					else if (_u()) e.flags |= 8192;
					else throw Xb = Kb, Wb;
				}
			} else e.flags &= -16777217;
		}
		function vc(e, t) {
			if (t.type !== "stylesheet" || (t.state.loading & BT) !== IT) e.flags &= -16777217;
			else if (e.flags |= 16777216, !Rp(t)) {
				if (_u()) e.flags |= 8192;
				else throw Xb = Kb, Wb;
			}
		}
		function yc(e, t) {
			t !== null && (e.flags |= 4), e.flags & 16384 && (t = e.tag === 22 ? 536870912 : $e(), e.lanes |= t, FC |= t);
		}
		function bc(e, t) {
			if (!G) switch (e.tailMode) {
				case "visible": break;
				case "collapsed":
					for (var n = e.tail, r = null; n !== null;) n.alternate !== null && (r = n), n = n.sibling;
					r === null ? t || e.tail === null ? e.tail = null : e.tail.sibling = null : r.sibling = null;
					break;
				default:
					for (t = e.tail, n = null; t !== null;) t.alternate !== null && (n = t), t = t.sibling;
					n === null ? e.tail = null : n.sibling = null;
			}
		}
		function xc(e) {
			var t = e.alternate !== null && e.alternate.child === e.child, n = 0, r = 0;
			if (t) {
				if ((e.mode & W) !== U) {
					for (var i = e.selfBaseDuration, a = e.child; a !== null;) n |= a.lanes | a.childLanes, r |= a.subtreeFlags & 1206910976, r |= a.flags & 1206910976, i += a.treeBaseDuration, a = a.sibling;
					e.treeBaseDuration = i;
				} else for (i = e.child; i !== null;) n |= i.lanes | i.childLanes, r |= i.subtreeFlags & 1206910976, r |= i.flags & 1206910976, i.return = e, i = i.sibling;
			} else if ((e.mode & W) !== U) {
				i = e.actualDuration, a = e.selfBaseDuration;
				for (var o = e.child; o !== null;) n |= o.lanes | o.childLanes, r |= o.subtreeFlags, r |= o.flags, i += o.actualDuration, a += o.treeBaseDuration, o = o.sibling;
				e.actualDuration = i, e.treeBaseDuration = a;
			} else for (i = e.child; i !== null;) n |= i.lanes | i.childLanes, r |= i.subtreeFlags, r |= i.flags, i.return = e, i = i.sibling;
			return e.subtreeFlags |= r, e.childLanes = n, t;
		}
		function Sc(e, t, n) {
			var r = t.pendingProps;
			switch (ni(t), t.tag) {
				case 16:
				case 15:
				case 0:
				case 11:
				case 7:
				case 8:
				case 12:
				case 9:
				case 14: return xc(t), null;
				case 1: return xc(t), null;
				case 3: return n = t.stateNode, r = null, e !== null && (r = e.memoizedState.cache), t.memoizedState.cache !== r && (t.flags |= 2048), yi(vy, t), we(t), n.pendingContext && (n.context = n.pendingContext, n.pendingContext = null), (e === null || e.child === null) && (fi(t) ? (gi(), gc(t)) : e === null || e.memoizedState.isDehydrated && !(t.flags & 256) || (t.flags |= 1024, mi())), xc(t), null;
				case 26:
					var i = t.type, a = t.memoizedState;
					return e === null ? (gc(t), a === null ? (xc(t), _c(t, i, null, r, n)) : (xc(t), vc(t, a))) : a ? a === e.memoizedState ? (xc(t), t.flags &= -16777217) : (gc(t), xc(t), vc(t, a)) : (e = e.memoizedProps, e !== r && gc(t), xc(t), _c(t, i, e, r, n)), null;
				case 27:
					if (De(t), n = Se(th.current), i = t.type, e !== null && t.stateNode != null) e.memoizedProps !== r && gc(t);
					else {
						if (!r) {
							if (t.stateNode === null) throw Error("We must have new props for new mounts. This error is likely caused by a bug in React. Please file an issue.");
							return xc(t), t.subtreeFlags &= -33554433, null;
						}
						e = Te(), fi(t) ? ui(t, e) : (e = gp(i, r, n, e, !0), t.stateNode = e, gc(t));
					}
					return xc(t), t.subtreeFlags &= -33554433, null;
				case 5:
					if (De(t), i = t.type, e !== null && t.stateNode != null) e.memoizedProps !== r && gc(t);
					else {
						if (!r) {
							if (t.stateNode === null) throw Error("We must have new props for new mounts. This error is likely caused by a bug in React. Please file an issue.");
							return xc(t), t.subtreeFlags &= -33554433, null;
						}
						var o = Te();
						if (fi(t)) ui(t, o);
						else {
							switch (a = Se(th.current), fn(i, o.ancestorInfo), o = o.context, a = Yd(a), o) {
								case vT:
									a = a.createElementNS(Lg, i);
									break;
								case yT:
									a = a.createElementNS(Ig, i);
									break;
								default: switch (i) {
									case "svg":
										a = a.createElementNS(Lg, i);
										break;
									case "math":
										a = a.createElementNS(Ig, i);
										break;
									case "script":
										a = a.createElement("div"), ST || $d(r) || (console.error("Encountered a script tag while rendering React component. Scripts inside React components are never executed when rendering on the client. Consider using template tag instead (https://developer.mozilla.org/en-US/docs/Web/HTML/Element/template)."), ST = !0), a.innerHTML = "<script><\/script>", a = a.removeChild(a.firstChild);
										break;
									case "select":
										a = typeof r.is == "string" ? a.createElement("select", { is: r.is }) : a.createElement("select"), r.multiple ? a.multiple = !0 : r.size && (a.size = r.size);
										break;
									default: a = typeof r.is == "string" ? a.createElement(i, { is: r.is }) : a.createElement(i), i.indexOf("-") === -1 && (i !== i.toLowerCase() && console.error("<%s /> is using incorrect casing. Use PascalCase for React components, or lowercase for HTML elements.", i), Object.prototype.toString.call(a) !== "[object HTMLUnknownElement]" || _h.call(CT, i) || (CT[i] = !0, console.error("The tag <%s> is unrecognized in this browser. If you meant to render a React component, start its name with an uppercase letter.", i)));
								}
							}
							a[Kh] = t, a[qh] = r;
							a: for (o = t.child; o !== null;) {
								if (o.tag === 5 || o.tag === 6) a.appendChild(o.stateNode);
								else if (o.tag !== 4 && o.tag !== 27 && o.child !== null) {
									o.child.return = o, o = o.child;
									continue;
								}
								if (o === t) break a;
								for (; o.sibling === null;) {
									if (o.return === null || o.return === t) break a;
									o = o.return;
								}
								o.sibling.return = o.return, o = o.sibling;
							}
							t.stateNode = a;
							a: switch (Fd(a, i, r), i) {
								case "button":
								case "input":
								case "select":
								case "textarea":
									r = !!r.autoFocus;
									break a;
								case "img":
									r = !0;
									break a;
								default: r = !1;
							}
							r && gc(t);
						}
					}
					return xc(t), t.subtreeFlags &= -33554433, _c(t, t.type, e === null ? null : e.memoizedProps, t.pendingProps, n), null;
				case 6:
					if (e && t.stateNode != null) e.memoizedProps !== r && gc(t);
					else {
						if (typeof r != "string" && t.stateNode === null) throw Error("We must have new props for new mounts. This error is likely caused by a bug in React. Please file an issue.");
						if (e = Se(th.current), n = Te(), fi(t)) {
							if (e = t.stateNode, n = t.memoizedProps, i = !iy, r = null, a = ny, a !== null) switch (a.tag) {
								case 3:
									i && (i = cp(e, n, r), i !== null && (oi(t, 0).serverProps = i));
									break;
								case 27:
								case 5: r = a.memoizedProps, i && (i = cp(e, n, r), i !== null && (oi(t, 0).serverProps = i));
							}
							e[Kh] = t, e = !!(e.nodeValue === n || r !== null && !0 === r.suppressHydrationWarning || Nd(e.nodeValue, n)), e || li(t, !0);
						} else i = n.ancestorInfo.current, i != null && pn(r, i.tag, n.ancestorInfo.implicitRootScope), e = Yd(e).createTextNode(r), e[Kh] = t, t.stateNode = e;
					}
					return xc(t), null;
				case 31:
					if (n = t.memoizedState, e === null || e.memoizedState !== null) {
						if (r = fi(t), n !== null) {
							if (e === null) {
								if (!r) throw Error("A dehydrated suspense component was completed without a hydrated node. This is probably a bug in React.");
								if (e = t.memoizedState, e = e === null ? null : e.dehydrated, !e) throw Error("Expected to have a hydrated activity instance. This error is likely caused by a bug in React. Please file an issue.");
								e[Kh] = t, xc(t), (t.mode & W) !== U && n !== null && (e = t.child, e !== null && (t.treeBaseDuration -= e.treeBaseDuration));
							} else gi(), pi(), !(t.flags & 128) && (n = t.memoizedState = null), t.flags |= 4, xc(t), (t.mode & W) !== U && n !== null && (e = t.child, e !== null && (t.treeBaseDuration -= e.treeBaseDuration));
							e = !1;
						} else n = mi(), e !== null && e.memoizedState !== null && (e.memoizedState.hydrationErrors = n), e = !0;
						if (!e) return t.flags & 256 ? (Va(t), t) : (Va(t), null);
						if (t.flags & 128) throw Error("Client rendering an Activity suspended it again. This is a bug in React.");
					}
					return xc(t), null;
				case 13:
					if (r = t.memoizedState, e === null || e.memoizedState !== null && e.memoizedState.dehydrated !== null) {
						if (i = r, a = fi(t), i !== null && i.dehydrated !== null) {
							if (e === null) {
								if (!a) throw Error("A dehydrated suspense component was completed without a hydrated node. This is probably a bug in React.");
								if (a = t.memoizedState, a = a === null ? null : a.dehydrated, !a) throw Error("Expected to have a hydrated suspense instance. This error is likely caused by a bug in React. Please file an issue.");
								a[Kh] = t, xc(t), (t.mode & W) !== U && i !== null && (i = t.child, i !== null && (t.treeBaseDuration -= i.treeBaseDuration));
							} else gi(), pi(), !(t.flags & 128) && (i = t.memoizedState = null), t.flags |= 4, xc(t), (t.mode & W) !== U && i !== null && (i = t.child, i !== null && (t.treeBaseDuration -= i.treeBaseDuration));
							i = !1;
						} else i = mi(), e !== null && e.memoizedState !== null && (e.memoizedState.hydrationErrors = i), i = !0;
						if (!i) return t.flags & 256 ? (Va(t), t) : (Va(t), null);
					}
					return Va(t), t.flags & 128 ? (t.lanes = n, (t.mode & W) !== U && Zi(t), t) : (n = r !== null, e = e !== null && e.memoizedState !== null, n && (r = t.child, i = null, r.alternate !== null && r.alternate.memoizedState !== null && r.alternate.memoizedState.cachePool !== null && (i = r.alternate.memoizedState.cachePool.pool), a = null, r.memoizedState !== null && r.memoizedState.cachePool !== null && (a = r.memoizedState.cachePool.pool), a !== i && (r.flags |= 2048)), n !== e && n && (t.child.flags |= 8192), yc(t, t.updateQueue), xc(t), (t.mode & W) !== U && n && (e = t.child, e !== null && (t.treeBaseDuration -= e.treeBaseDuration)), null);
				case 4: return we(t), e === null && vd(t.stateNode.containerInfo), t.flags |= 67108864, xc(t), null;
				case 10: return yi(t.type, t), xc(t), null;
				case 19:
					if (Ua(t), r = t.memoizedState, r === null) return xc(t), null;
					if (i = !!(t.flags & 128), a = r.rendering, a === null) {
						if (i) bc(r, !1);
						else {
							if (AC !== oC || e !== null && e.flags & 128) for (e = t.child; e !== null;) {
								if (a = Wa(e), a !== null) {
									for (t.flags |= 128, bc(r, !1), e = a.updateQueue, t.updateQueue = e, yc(t, e), t.subtreeFlags = 0, e = n, n = t.child; n !== null;) Gr(n, e), n = n.sibling;
									return Ha(t, xx.current & yx | bx), G && $r(t, r.treeForkCount), t.child;
								}
								e = e.sibling;
							}
							r.tail !== null && Sh() > HC && (t.flags |= 128, i = !0, bc(r, !1), t.lanes = 4194304);
						}
					} else {
						if (!i) {
							if (e = Wa(a), e !== null) {
								if (t.flags |= 128, i = !0, e = e.updateQueue, t.updateQueue = e, yc(t, e), bc(r, !0), r.tail === null && r.tailMode !== "collapsed" && r.tailMode !== "visible" && !a.alternate && !G) return xc(t), null;
							} else 2 * Sh() - r.renderingStartTime > HC && n !== 536870912 && (t.flags |= 128, i = !0, bc(r, !1), t.lanes = 4194304);
						}
						r.isBackwards ? (a.sibling = t.child, t.child = a) : (e = r.last, e === null ? t.child = a : e.sibling = a, r.last = a);
					}
					if (r.tail !== null) {
						e = r.tail;
						a: {
							for (n = e; n !== null;) {
								if (n.alternate !== null) {
									n = !1;
									break a;
								}
								n = n.sibling;
							}
							n = !0;
						}
						return r.rendering = e, r.tail = e.sibling, r.renderingStartTime = Sh(), e.sibling = null, a = xx.current, a = i ? a & yx | bx : a & yx, r.tailMode === "visible" || r.tailMode === "collapsed" || !n || G ? Ha(t, a) : (n = a, xe(_x, t, t), xe(xx, n, t), vx === null && (vx = t)), G && $r(t, r.treeForkCount), e;
					}
					return xc(t), null;
				case 22:
				case 23: return Va(t), Ia(t), r = t.memoizedState !== null, e === null ? r && (t.flags |= 8192) : e.memoizedState !== null !== r && (t.flags |= 8192), r ? n & 536870912 && !(t.flags & 128) && (xc(t), t.subtreeFlags & 6 && (t.flags |= 8192)) : xc(t), n = t.updateQueue, n !== null && yc(t, n.retryQueue), n = null, e !== null && e.memoizedState !== null && e.memoizedState.cachePool !== null && (n = e.memoizedState.cachePool.pool), r = null, t.memoizedState !== null && t.memoizedState.cachePool !== null && (r = t.memoizedState.cachePool.pool), r !== n && (t.flags |= 2048), e !== null && be(pb, t), null;
				case 24: return n = null, e !== null && (n = e.memoizedState.cache), t.memoizedState.cache !== n && (t.flags |= 2048), yi(vy, t), xc(t), null;
				case 25: return null;
				case 30: return t.flags |= 33554432, xc(t), null;
			}
			throw Error("Unknown unit of work tag (" + t.tag + "). This error is likely caused by a bug in React. Please file an issue.");
		}
		function Cc(e, t) {
			switch (ni(t), t.tag) {
				case 1: return e = t.flags, e & 65536 ? (t.flags = e & -65537 | 128, (t.mode & W) !== U && Zi(t), t) : null;
				case 3: return yi(vy, t), we(t), e = t.flags, e & 65536 && !(e & 128) ? (t.flags = e & -65537 | 128, t) : null;
				case 26:
				case 27:
				case 5: return De(t), null;
				case 31:
					if (t.memoizedState !== null) {
						if (Va(t), t.alternate === null) throw Error("Threw in newly mounted dehydrated component. This is likely a bug in React. Please file an issue.");
						pi();
					}
					return e = t.flags, e & 65536 ? (t.flags = e & -65537 | 128, (t.mode & W) !== U && Zi(t), t) : null;
				case 13:
					if (Va(t), e = t.memoizedState, e !== null && e.dehydrated !== null) {
						if (t.alternate === null) throw Error("Threw in newly mounted dehydrated component. This is likely a bug in React. Please file an issue.");
						pi();
					}
					return e = t.flags, e & 65536 ? (t.flags = e & -65537 | 128, (t.mode & W) !== U && Zi(t), t) : null;
				case 19: return Ua(t), e = t.flags, e & 65536 ? (t.flags = e & -65537 | 128, e = t.memoizedState, e !== null && (e.rendering = null, e.tail = null), t.flags |= 4, t) : null;
				case 4: return we(t), null;
				case 10: return yi(t.type, t), null;
				case 22:
				case 23: return Va(t), Ia(t), e !== null && be(pb, t), e = t.flags, e & 65536 ? (t.flags = e & -65537 | 128, (t.mode & W) !== U && Zi(t), t) : null;
				case 24: return yi(vy, t), null;
				case 25: return null;
				default: return null;
			}
		}
		function wc(e, t) {
			switch (ni(t), t.tag) {
				case 3:
					yi(vy, t), we(t);
					break;
				case 26:
				case 27:
				case 5:
					De(t);
					break;
				case 4:
					we(t);
					break;
				case 31:
					t.memoizedState !== null && Va(t);
					break;
				case 13:
					Va(t);
					break;
				case 19:
					Ua(t);
					break;
				case 10:
					yi(t.type, t);
					break;
				case 22:
				case 23:
					Va(t), Ia(t), e !== null && be(pb, t);
					break;
				case 24: yi(vy, t);
			}
		}
		function Tc(e) {
			return (e.mode & W) !== U;
		}
		function Ec(e, t) {
			Tc(e) ? (Xi(), Oc(t, e), Ji()) : Oc(t, e);
		}
		function Dc(e, t, n) {
			Tc(e) ? (Xi(), kc(n, e, t), Ji()) : kc(n, e, t);
		}
		function Oc(e, t) {
			try {
				var n = t.updateQueue, r = n === null ? null : n.lastEffect;
				if (r !== null) {
					var i = r.next;
					n = i;
					do {
						if ((n.tag & e) === e && (r = void 0, (e & wx) !== Sx && (Tw = !0), r = w(t, Rb, n), (e & wx) !== Sx && (Tw = !1), r !== void 0 && typeof r != "function")) {
							var a = void 0;
							a = (n.tag & Tx) === 0 ? (n.tag & wx) === 0 ? "useEffect" : "useInsertionEffect" : "useLayoutEffect";
							var o = void 0;
							o = r === null ? " You returned null. If your effect does not require clean up, return undefined (or nothing)." : typeof r.then == "function" ? "\n\nIt looks like you wrote " + a + "(async () => ...) or returned a Promise. Instead, write the async function inside your effect and call it immediately:\n\n" + a + "(() => {\n  async function fetchData() {\n    // You can await here\n    const response = await MyAPI.getData(someId);\n    // ...\n  }\n  fetchData();\n}, [someId]); // Or [] if effect doesn't need props or state\n\nLearn more about data fetching with Hooks: https://react.dev/link/hooks-data-fetching" : " You returned: " + r, w(t, function(e, t) {
								console.error("%s must not return anything besides a function, which is used for clean-up.%s", e, t);
							}, a, o);
						}
						n = n.next;
					} while (n !== i);
				}
			} catch (e) {
				Ku(t, t.return, e);
			}
		}
		function kc(e, t, n) {
			try {
				var r = t.updateQueue, i = r === null ? null : r.lastEffect;
				if (i !== null) {
					var a = i.next;
					r = a;
					do {
						if ((r.tag & e) === e) {
							var o = r.inst, s = o.destroy;
							s !== void 0 && (o.destroy = void 0, (e & wx) !== Sx && (Tw = !0), i = t, w(i, Bb, i, n, s), (e & wx) !== Sx && (Tw = !1));
						}
						r = r.next;
					} while (r !== a);
				}
			} catch (e) {
				Ku(t, t.return, e);
			}
		}
		function Ac(e, t) {
			Tc(e) ? (Xi(), Oc(t, e), Ji()) : Oc(t, e);
		}
		function jc(e, t, n) {
			Tc(e) ? (Xi(), kc(n, e, t), Ji()) : kc(n, e, t);
		}
		function Mc(e) {
			var t = e.updateQueue;
			if (t !== null) {
				var n = e.stateNode;
				e.type.defaultProps || "ref" in e.memoizedProps || bS || (n.props !== e.memoizedProps && console.error("Expected %s props to match memoized props before processing the update queue. This might either be because of a bug in React, or because a component reassigns its own `this.props`. Please file an issue.", S(e) || "instance"), n.state !== e.memoizedState && console.error("Expected %s state to match memoized state before processing the update queue. This might either be because of a bug in React, or because a component reassigns its own `this.state`. Please file an issue.", S(e) || "instance"));
				try {
					w(e, Na, t, n);
				} catch (t) {
					Ku(e, e.return, t);
				}
			}
		}
		function Nc(e, t, n) {
			return e.getSnapshotBeforeUpdate(t, n);
		}
		function Pc(e, t) {
			var n = t.memoizedProps, r = t.memoizedState;
			t = e.stateNode, e.type.defaultProps || "ref" in e.memoizedProps || bS || (t.props !== e.memoizedProps && console.error("Expected %s props to match memoized props before getSnapshotBeforeUpdate. This might either be because of a bug in React, or because a component reassigns its own `this.props`. Please file an issue.", S(e) || "instance"), t.state !== e.memoizedState && console.error("Expected %s state to match memoized state before getSnapshotBeforeUpdate. This might either be because of a bug in React, or because a component reassigns its own `this.state`. Please file an issue.", S(e) || "instance"));
			try {
				var i = Ds(e.type, n), a = w(e, Nc, t, i, r);
				n = ES, a !== void 0 || n.has(e.type) || (n.add(e.type), w(e, function() {
					console.error("%s.getSnapshotBeforeUpdate(): A snapshot value (or null) must be returned. You have returned undefined.", S(e));
				})), t.__reactInternalSnapshotBeforeUpdate = a;
			} catch (t) {
				Ku(e, e.return, t);
			}
		}
		function Fc(e, t, n) {
			n.props = Ds(e.type, e.memoizedProps), n.state = e.memoizedState, Tc(e) ? (Xi(), w(e, Ib, e, t, n), Ji()) : w(e, Ib, e, t, n);
		}
		function Ic(e) {
			var t = e.ref;
			if (t !== null) {
				switch (e.tag) {
					case 26:
					case 27:
					case 5:
						var n = e.stateNode;
						break;
					case 30:
						n = e.stateNode;
						var r = dr(e.memoizedProps, n);
						(n.ref === null || n.ref.name !== r) && (n.ref = Pf(r)), n = n.ref;
						break;
					case 7:
						e.stateNode === null && (n = new Ff(e), x(e, Xf, n), e.stateNode = n), n = e.stateNode;
						break;
					default: n = e.stateNode;
				}
				if (typeof t == "function") {
					if (Tc(e)) try {
						Xi(), e.refCleanup = t(n);
					} finally {
						Ji();
					}
					else e.refCleanup = t(n);
				} else typeof t == "string" ? console.error("String refs are no longer supported.") : t.hasOwnProperty("current") || console.error("Unexpected ref object provided for %s. Use either a ref-setter function or React.createRef().", S(e)), t.current = n;
			}
		}
		function Lc(e, t) {
			try {
				w(e, Ic, e);
			} catch (n) {
				Ku(e, t, n);
			}
		}
		function Rc(e, t) {
			var n = e.ref, r = e.refCleanup;
			if (n !== null) {
				if (typeof r == "function") try {
					if (Tc(e)) try {
						Xi(), w(e, r);
					} finally {
						Ji(e);
					}
					else w(e, r);
				} catch (n) {
					Ku(e, t, n);
				} finally {
					e.refCleanup = null, e = e.alternate, e != null && (e.refCleanup = null);
				}
				else if (typeof n == "function") try {
					if (Tc(e)) try {
						Xi(), w(e, n, null);
					} finally {
						Ji(e);
					}
					else w(e, n, null);
				} catch (n) {
					Ku(e, t, n);
				}
				else n.current = null;
			}
		}
		function zc(e, t, n, r) {
			var i = e.memoizedProps, a = i.id, o = i.onCommit;
			i = i.onRender, t = t === null ? "mount" : "update", ob && (t = "nested-update"), typeof i == "function" && i(a, t, e.actualDuration, e.treeBaseDuration, e.actualStartTime, n), typeof o == "function" && o(a, t, r, n);
		}
		function Bc(e, t, n, r) {
			var i = e.memoizedProps;
			e = i.id, i = i.onPostCommit, t = t === null ? "mount" : "update", ob && (t = "nested-update"), typeof i == "function" && i(e, t, r, n);
		}
		function Vc(e, t) {
			if ((e.tag === 5 || e.tag === 27 || e.tag === 6) && e.alternate === null && t !== null) for (var n = 0; n < t.length; n++) Qf(e.stateNode, t[n]);
		}
		function Hc(e) {
			for (var t = e.return; t !== null && (Gc(t) && Qf(e.stateNode, t.stateNode), !Wc(t));) t = t.return;
		}
		function Uc(e) {
			for (var t = e.return; t !== null && (Gc(t) && $f(e.stateNode, t.stateNode), !Wc(t));) t = t.return;
		}
		function Wc(e) {
			return e.tag === 5 || e.tag === 3 || e.tag === 27;
		}
		function Gc(e) {
			return e && e.tag === 7 && e.stateNode !== null;
		}
		function Kc(e) {
			var t = e.type, n = e.memoizedProps, r = e.stateNode;
			try {
				w(e, of, r, t, n, e);
			} catch (t) {
				Ku(e, e.return, t);
			}
		}
		function qc(e, t, n) {
			try {
				w(e, cf, e.stateNode, e.type, n, t, e);
			} catch (t) {
				Ku(e, e.return, t);
			}
		}
		function Jc(e) {
			return e.tag === 5 || e.tag === 3 || e.tag === 26 || e.tag === 27 && ff(e.type) || e.tag === 4;
		}
		function Yc(e) {
			a: for (;;) {
				for (; e.sibling === null;) {
					if (e.return === null || Jc(e.return)) return null;
					e = e.return;
				}
				for (e.sibling.return = e.return, e = e.sibling; e.tag !== 5 && e.tag !== 6 && e.tag !== 18;) {
					if (e.tag === 27 && ff(e.type) || e.flags & 2 || e.child === null || e.tag === 4) continue a;
					e.child.return = e, e = e.child;
				}
				if (!(e.flags & 2)) return e.stateNode;
			}
		}
		function Xc(e, t, n, r) {
			var i = e.tag;
			if (i === 5 || i === 6) i = e.stateNode, t ? (df(n), (n.nodeType === 9 ? n.body : n.nodeName === "HTML" ? n.ownerDocument.body : n).insertBefore(i, t)) : (df(n), t = n.nodeType === 9 ? n.body : n.nodeName === "HTML" ? n.ownerDocument.body : n, t.appendChild(i), i = n._reactRootContainer, i != null || t.onclick !== null || (t.onclick = Tn)), Vc(e, r), B = !0;
			else if (i !== 4 && (i === 27 && (Vc(e, r), r = null, ff(e.type) && (n = e.stateNode, t = null)), e = e.child, e !== null)) for (Xc(e, t, n, r), e = e.sibling; e !== null;) Xc(e, t, n, r), e = e.sibling;
		}
		function Zc(e, t, n, r) {
			var i = e.tag;
			if (i === 5 || i === 6) i = e.stateNode, t ? n.insertBefore(i, t) : n.appendChild(i), Vc(e, r), B = !0;
			else if (i !== 4 && (i === 27 && (Vc(e, r), r = null, ff(e.type) && (n = e.stateNode)), e = e.child, e !== null)) for (Zc(e, t, n, r), e = e.sibling; e !== null;) Zc(e, t, n, r), e = e.sibling;
		}
		function Qc(e) {
			for (var t, n = e.return; n !== null;) {
				if (Jc(n)) {
					t = n;
					break;
				}
				n = n.return;
			}
			n = null;
			for (var r = e.return; r !== null;) {
				if (Gc(r)) {
					var i = r.stateNode;
					n === null ? n = [i] : n.push(i);
				}
				if (Wc(r)) break;
				r = r.return;
			}
			if (t == null) throw Error("Expected to find a host parent. This error is likely caused by a bug in React. Please file an issue.");
			switch (t.tag) {
				case 27:
					t = t.stateNode, r = Yc(e), Zc(e, r, t, n);
					break;
				case 5:
					r = t.stateNode, t.flags & 32 && (lf(r), t.flags &= -33), t = Yc(e), Zc(e, t, r, n);
					break;
				case 3:
				case 4:
					t = t.stateNode.containerInfo, r = Yc(e), Xc(e, r, t, n);
					break;
				default: throw Error("Invalid host parent fiber. This error is likely caused by a bug in React. Please file an issue.");
			}
		}
		function $c(e) {
			var t = e.stateNode, n = e.memoizedProps;
			try {
				w(e, _p, e.type, n, t, e);
			} catch (t) {
				Ku(e, e.return, t);
			}
		}
		function el(e) {
			(e.tag === 30 || e.subtreeFlags & 33554432) && (DS = !0);
		}
		function tl() {
			var e = kS;
			return kS = null, e;
		}
		function nl(e, t, n, r, i) {
			return AS = 0, (t = rl(e.child, t, n, r, i)) && e._debugTask != null && rb === null && (rb = e._debugTask), t;
		}
		function rl(e, t, n, r, i) {
			for (var a = !1; e !== null;) {
				if (e.tag === 5) {
					var o = e.stateNode;
					if (r !== null) {
						var s = Df(o);
						r.push(s), s.view && (a = !0);
					} else a || Df(o).view && (a = !0);
					DS = !0, wf(o, AS === 0 ? t : t + "_" + AS, n), AS++;
				} else e.tag === 22 && e.memoizedState !== null || e.tag === 30 && i || rl(e.child, t, n, r, i) && (a = !0);
				e = e.sibling;
			}
			return a;
		}
		function il(e, t) {
			for (; e !== null;) e.tag === 5 ? Tf(e.stateNode, e.memoizedProps) : e.tag === 22 && e.memoizedState !== null || e.tag === 30 && t || il(e.child, t), e = e.sibling;
		}
		function al(e) {
			if (e.subtreeFlags & 18874368) for (e = e.child; e !== null;) {
				if ((e.tag !== 22 || e.memoizedState === null) && (al(e), e.tag === 30 && e.flags & 18874368 && e.stateNode.paired)) {
					var t = e.memoizedProps;
					if (t.name == null || t.name === "auto") throw Error("Found a pair with an auto name. This is a bug in React.");
					var n = t.name;
					t = pr(t.default, t.share), t !== "none" && (nl(e, n, t, null, !1) || il(e.child, !1));
				}
				e = e.sibling;
			}
		}
		function ol(e, t) {
			if (e.tag === 30) {
				var n = e.stateNode, r = e.memoizedProps, i = dr(r, n), a = pr(r.default, n.paired ? r.share : r.enter);
				a === "none" ? al(e) : nl(e, i, a, null, !1) ? (al(e), n.paired || t || ou(e, r.onEnter)) : il(e.child, !1);
			} else if (e.subtreeFlags & 33554432) for (e = e.child; e !== null;) ol(e, t), e = e.sibling;
			else al(e);
		}
		function sl(e) {
			if (OS !== null && OS.size !== 0) {
				var t = OS;
				if (e.subtreeFlags & 18874368) for (e = e.child; e !== null;) {
					if (e.tag !== 22 || e.memoizedState === null) {
						if (e.tag === 30 && e.flags & 18874368) {
							var n = e.memoizedProps, r = n.name;
							if (r != null && r !== "auto") {
								var i = t.get(r);
								if (i !== void 0) {
									var a = pr(n.default, n.share);
									if (a !== "none" && (nl(e, r, a, null, !1) ? (a = e.stateNode, i.paired = a, a.paired = i, ou(e, n.onShare)) : il(e.child, !1)), t.delete(r), t.size === 0) break;
								}
							}
						}
						sl(e);
					}
					e = e.sibling;
				}
			}
		}
		function cl(e) {
			if (e.tag === 30) {
				var t = e.memoizedProps, n = dr(t, e.stateNode), r = OS === null ? void 0 : OS.get(n), i = pr(t.default, r === void 0 ? t.exit : t.share);
				i !== "none" && (nl(e, n, i, null, !1) ? r === void 0 ? ou(e, t.onExit) : (i = e.stateNode, r.paired = i, i.paired = r, OS.delete(n), ou(e, t.onShare)) : il(e.child, !1)), OS !== null && sl(e);
			} else if (e.subtreeFlags & 33554432) for (e = e.child; e !== null;) cl(e), e = e.sibling;
			else OS !== null && sl(e);
		}
		function ll(e) {
			for (e = e.child; e !== null;) {
				if (e.tag === 30) {
					var t = e.memoizedProps, n = dr(t, e.stateNode);
					t = pr(t.default, t.update), e.flags &= -5, t !== "none" && nl(e, n, t, e.memoizedState = [], !1);
				} else e.subtreeFlags & 33554432 && ll(e);
				e = e.sibling;
			}
		}
		function ul(e) {
			if (e.subtreeFlags & 18874368) for (e = e.child; e !== null;) {
				if (e.tag !== 22 || e.memoizedState === null) {
					if (e.tag === 30 && e.flags & 18874368) {
						var t = e.stateNode;
						t.paired !== null && (t.paired = null, il(e.child, !1));
					}
					ul(e);
				}
				e = e.sibling;
			}
		}
		function dl(e) {
			if (e.tag === 30) e.stateNode.paired = null, il(e.child, !1), ul(e);
			else if (e.subtreeFlags & 33554432) for (e = e.child; e !== null;) dl(e), e = e.sibling;
			else ul(e);
		}
		function fl(e) {
			for (e = e.child; e !== null;) e.tag === 30 ? il(e.child, !1) : e.subtreeFlags & 33554432 && fl(e), e = e.sibling;
		}
		function pl(e, t, n, r, i, a, o) {
			for (var s = !1; t !== null;) {
				if (t.tag === 5) {
					var c = t.stateNode;
					if (a !== null && AS < a.length) {
						var l = a[AS], u = Df(c);
						(l.view || u.view) && (s = !0);
						var d;
						if (d = !(e.flags & 4)) {
							if (u.clip) d = !0;
							else {
								d = l.rect;
								var f = u.rect;
								d = d.y !== f.y || d.x !== f.x || d.height !== f.height || d.width !== f.width;
							}
						}
						d && (e.flags |= 4), u.abs ? u = !l.abs : (l = l.rect, u = u.rect, u = l.height !== u.height || l.width !== u.width), u && (e.flags |= 32);
					} else e.flags |= 32;
					e.flags & 4 && wf(c, AS === 0 ? n : n + "_" + AS, i), s && e.flags & 4 || (kS === null && (kS = []), kS.push(c, AS === 0 ? r : r + "_" + AS, t.memoizedProps)), AS++;
				} else (t.tag !== 22 || t.memoizedState === null) && (t.tag === 30 && o ? e.flags |= t.flags & 32 : pl(e, t.child, n, r, i, a, o) && (s = !0));
				t = t.sibling;
			}
			return s;
		}
		function ml(e, t) {
			for (e = e.child; e !== null;) {
				if (e.tag === 30) {
					var n = e.memoizedProps, r = e.stateNode, i = dr(n, r), a = pr(n.default, n.update);
					if (t) {
						r = r.clones;
						var o = r === null ? null : r.map(Of);
					} else o = e.memoizedState, e.memoizedState = null;
					r = e;
					var s = e.child, c = i;
					AS = 0, a = pl(r, s, c, i, a, o, !1), e.flags & 4 && a && (t || ou(e, n.onUpdate));
				} else e.subtreeFlags & 33554432 && ml(e, t);
				e = e.sibling;
			}
		}
		function hl(e) {
			var t = e.memoizedProps.name;
			if (t != null && t !== "auto") {
				var n = jS.get(t);
				if (n !== void 0) {
					if (n !== e && n !== e.alternate && !MS[t]) {
						MS[t] = !0;
						var r = JSON.stringify(t);
						w(e, function() {
							console.error("There are two <ViewTransition name=%s> components with the same name mounted at the same time. This is not supported and will cause View Transitions to error. Try to use a more unique name e.g. by using a namespace prefix and adding the id of an item to the name.", r);
						}), w(n, function() {
							console.error("The existing <ViewTransition name=%s> duplicate has this stack trace.", r);
						});
					}
				} else jS.set(t, e);
			}
		}
		function gl(e) {
			var t = e.memoizedProps.name;
			if (t != null && t !== "auto") {
				var n = jS.get(t);
				n === void 0 || n !== e && n !== e.alternate || jS.delete(t);
			}
		}
		function _l(e, t) {
			return t.tag === 31 ? (t = t.memoizedState, e.memoizedState !== null && t === null) : t.tag === 13 ? (e = e.memoizedState, t = t.memoizedState, e !== null && e.dehydrated !== null && (t === null || t.dehydrated === null)) : t.tag === 3 && e.memoizedState.isDehydrated && !(t.flags & 256);
		}
		function vl(e, t, n) {
			if (e = e.containerInfo, bT = gE, e = ar(e), or(e)) {
				if ("selectionStart" in e) var r = {
					start: e.selectionStart,
					end: e.selectionEnd
				};
				else a: {
					r = (r = e.ownerDocument) && r.defaultView || window;
					var i = r.getSelection && r.getSelection();
					if (i && i.rangeCount !== 0) {
						r = i.anchorNode;
						var a = i.anchorOffset, o = i.focusNode;
						i = i.focusOffset;
						try {
							r.nodeType, o.nodeType;
						} catch {
							r = null;
							break a;
						}
						var s = 0, c = -1, l = -1, u = 0, d = 0, f = e, p = null;
						b: for (;;) {
							for (var m; f !== r || a !== 0 && f.nodeType !== 3 || (c = s + a), f !== o || i !== 0 && f.nodeType !== 3 || (l = s + i), f.nodeType === 3 && (s += f.nodeValue.length), (m = f.firstChild) !== null;) p = f, f = m;
							for (;;) {
								if (f === e) break b;
								if (p === r && ++u === a && (c = s), p === o && ++d === i && (l = s), (m = f.nextSibling) !== null) break;
								f = p, p = f.parentNode;
							}
							f = m;
						}
						r = c === -1 || l === -1 ? null : {
							start: c,
							end: l
						};
					} else r = null;
				}
				r ||= {
					start: 0,
					end: 0
				};
			} else r = null;
			for (xT = {
				focusedElem: e,
				selectionRange: r
			}, gE = !1, n = (n & 335544064) === n, VS = t, t = n ? 9270 : 1024; VS !== null;) {
				if (e = VS, n && (r = e.deletions, r !== null)) for (a = 0; a < r.length; a++) n && cl(r[a]);
				if (e.alternate === null && e.flags & 2) n && el(e), yl(n);
				else {
					if (e.tag === 22) {
						if (r = e.alternate, e.memoizedState !== null) {
							r !== null && r.memoizedState === null && n && cl(r), yl(n);
							continue;
						}
						if (r !== null && r.memoizedState !== null) {
							n && el(e), yl(n);
							continue;
						}
					}
					r = e.child, (e.subtreeFlags & t) !== 0 && r !== null ? (r.return = e, VS = r) : (n && ll(e), yl(n));
				}
			}
			OS = null;
		}
		function yl(e) {
			for (; VS !== null;) {
				var t = VS, n = t, r = e, i = n.alternate, a = n.flags;
				switch (n.tag) {
					case 0:
					case 11:
					case 15: break;
					case 1:
						a & 1024 && i !== null && Pc(n, i);
						break;
					case 3:
						if (a & 1024) {
							if (r = n.stateNode.containerInfo, n = r.nodeType, n === 9) ep(r);
							else if (n === 1) switch (r.nodeName) {
								case "HEAD":
								case "HTML":
								case "BODY":
									ep(r);
									break;
								default: r.textContent = "";
							}
						}
						break;
					case 5:
					case 26:
					case 27:
					case 6:
					case 4:
					case 17: break;
					case 30:
						r && i !== null && (r = i, i = n, n = dr(r.memoizedProps, r.stateNode), i = i.memoizedProps, i = pr(i.default, i.update), i !== "none" && nl(r, n, i, r.memoizedState = [], !0));
						break;
					default: if (a & 1024) throw Error("This unit of work tag should not have side-effects. This error is likely caused by a bug in React. Please file an issue.");
				}
				if (r = t.sibling, r !== null) {
					r.return = t.return, VS = r;
					break;
				}
				VS = t.return;
			}
		}
		function bl(e, t, n) {
			var r = zi(), i = Vi(), a = Ui(), o = Wi(), s = n.flags;
			switch (n.tag) {
				case 0:
				case 11:
				case 15:
					Il(e, n), s & 4 && Ec(n, Tx | Cx);
					break;
				case 1:
					if (Il(e, n), s & 4) {
						if (e = n.stateNode, t === null) n.type.defaultProps || "ref" in n.memoizedProps || bS || (e.props !== n.memoizedProps && console.error("Expected %s props to match memoized props before componentDidMount. This might either be because of a bug in React, or because a component reassigns its own `this.props`. Please file an issue.", S(n) || "instance"), e.state !== n.memoizedState && console.error("Expected %s state to match memoized state before componentDidMount. This might either be because of a bug in React, or because a component reassigns its own `this.state`. Please file an issue.", S(n) || "instance")), Tc(n) ? (Xi(), w(n, Ab, n, e), Ji()) : w(n, Ab, n, e);
						else {
							var c = Ds(n.type, t.memoizedProps);
							t = t.memoizedState, n.type.defaultProps || "ref" in n.memoizedProps || bS || (e.props !== n.memoizedProps && console.error("Expected %s props to match memoized props before componentDidUpdate. This might either be because of a bug in React, or because a component reassigns its own `this.props`. Please file an issue.", S(n) || "instance"), e.state !== n.memoizedState && console.error("Expected %s state to match memoized state before componentDidUpdate. This might either be because of a bug in React, or because a component reassigns its own `this.state`. Please file an issue.", S(n) || "instance")), Tc(n) ? (Xi(), w(n, Mb, n, e, c, t, e.__reactInternalSnapshotBeforeUpdate), Ji()) : w(n, Mb, n, e, c, t, e.__reactInternalSnapshotBeforeUpdate);
						}
					}
					s & 64 && Mc(n), s & 512 && Lc(n, n.return);
					break;
				case 3:
					if (t = Fi(), Il(e, n), s & 64 && (s = n.updateQueue, s !== null)) {
						if (c = null, n.child !== null) switch (n.child.tag) {
							case 27:
							case 5:
								c = n.child.stateNode;
								break;
							case 1: c = n.child.stateNode;
						}
						try {
							w(n, Na, s, c);
						} catch (e) {
							Ku(n, n.return, e);
						}
					}
					e.effectDuration += Ii(t);
					break;
				case 27: t === null && s & 4 && $c(n);
				case 26:
				case 5:
					if (Il(e, n), t === null) {
						if (s & 4) Kc(n);
						else if (s & 64) {
							e = n.type, t = n.memoizedProps, c = n.stateNode;
							try {
								w(n, sf, c, e, t, n);
							} catch (e) {
								Ku(n, n.return, e);
							}
						}
					}
					s & 512 && Lc(n, n.return);
					break;
				case 12:
					if (s & 4) {
						s = Fi(), Il(e, n), e = n.stateNode, e.effectDuration += Li(s);
						try {
							w(n, zc, n, t, Ty, e.effectDuration);
						} catch (e) {
							Ku(n, n.return, e);
						}
					} else Il(e, n);
					break;
				case 31:
					Il(e, n), s & 4 && Dl(e, n);
					break;
				case 13:
					Il(e, n), s & 4 && Ol(e, n), s & 64 && (e = n.memoizedState, e !== null && (e = e.dehydrated, e !== null && (s = Xu.bind(null, n), ap(e, s))));
					break;
				case 22:
					if (s = n.memoizedState !== null || IS, !s) {
						var l = t !== null && t.memoizedState !== null || LS;
						t = IS, c = LS, IS = s, (LS = l) && !c ? (s = FS, n.subtreeFlags & 8772 && (s |= PS), Bl(e, n, s), (n.mode & W) !== U && 0 <= K && 0 <= q && .05 < q - K && xr(n, K, q)) : Il(e, n), IS = t, LS = c;
					}
					break;
				case 30:
					s & 18874368 && hl(n), Il(e, n), s & 512 && Lc(n, n.return);
					break;
				case 7: s & 512 && Lc(n, n.return);
				default: Il(e, n);
			}
			(n.mode & W) !== U && 0 <= K && 0 <= q && ((My || .05 < Ay) && wr(n, K, q, Ay, jy), n.alternate === null && n.return !== null && n.return.alternate !== null && .05 < q - K && (_l(n.return.alternate, n.return) || br(n, K, q, "Mount"))), Bi(r), Hi(i), jy = a, My = o;
		}
		function xl(e, t) {
			for (e = e.child; e !== null;) Sl(e, t), e = e.sibling;
		}
		function Sl(e, t) {
			switch (e.tag) {
				case 5:
				case 26:
					try {
						var n = e.stateNode;
						t ? w(e, vf, n) : w(e, xf, e.stateNode, e.memoizedProps);
					} catch (t) {
						Ku(e, e.return, t);
					}
					Cl(e, t);
					break;
				case 6:
					try {
						var r = e.stateNode;
						t ? w(e, yf, r) : w(e, Sf, r, e.memoizedProps), B = !0;
					} catch (t) {
						Ku(e, e.return, t);
					}
					break;
				case 18:
					try {
						var i = e.stateNode;
						t ? w(e, _f, i) : w(e, bf, e.stateNode);
					} catch (t) {
						Ku(e, e.return, t);
					}
					break;
				case 22:
				case 23:
					e.memoizedState === null && xl(e, t);
					break;
				default: xl(e, t);
			}
		}
		function Cl(e, t) {
			if (e.subtreeFlags & 67108864) for (e = e.child; e !== null;) {
				a: {
					var n = e, r = t;
					switch (n.tag) {
						case 4:
							Sl(n, r);
							break a;
						case 22:
							n.memoizedState === null && Cl(n, r);
							break a;
						default: Cl(n, r);
					}
				}
				e = e.sibling;
			}
		}
		function wl(e) {
			var t = e.alternate;
			t !== null && (e.alternate = null, wl(t)), e.child = null, e.deletions = null, e.sibling = null, e.tag === 5 && (t = e.stateNode, t !== null && ft(t)), e.stateNode = null, e._debugOwner = null, e.return = null, e.dependencies = null, e.memoizedProps = null, e.memoizedState = null, e.pendingProps = null, e.stateNode = null, e.updateQueue = null;
		}
		function Tl(e, t, n) {
			for (n = n.child; n !== null;) El(e, t, n), n = n.sibling;
		}
		function El(e, t, n) {
			if (Mh && typeof Mh.onCommitFiberUnmount == "function") try {
				Mh.onCommitFiberUnmount(jh, n);
			} catch (e) {
				Nh || (Nh = !0, console.error("React instrumentation encountered an error: %o", e));
			}
			var r = zi(), i = Vi(), a = Ui(), o = Wi();
			switch (n.tag) {
				case 26:
					LS || Rc(n, t), Tl(e, t, n), n.memoizedState ? n.memoizedState.count-- : n.stateNode && !LS && (e = n.stateNode, e.parentNode.removeChild(e));
					break;
				case 27:
					LS || Rc(n, t), Uc(n);
					var s = JS, c = YS;
					ff(n.type) && (JS = n.stateNode, YS = !1), Tl(e, t, n), w(n, vp, n.stateNode, n.type, n.memoizedProps), JS = s, YS = c;
					break;
				case 5: LS || Rc(n, t), Uc(n);
				case 6:
					if (n.tag === 6 && Uc(n), s = JS, c = YS, JS = null, Tl(e, t, n), JS = s, YS = c, JS !== null) {
						if (YS) try {
							w(n, mf, JS, n.stateNode), B = !0;
						} catch (e) {
							Ku(n, t, e);
						}
						else try {
							w(n, pf, JS, n.stateNode), B = !0;
						} catch (e) {
							Ku(n, t, e);
						}
					}
					break;
				case 18:
					JS !== null && (YS ? (e = JS, hf(e.nodeType === 9 ? e.body : e.nodeName === "HTML" ? e.ownerDocument.body : e, n.stateNode), vm(e)) : hf(JS, n.stateNode));
					break;
				case 4:
					s = JS, c = YS, JS = n.stateNode.containerInfo, YS = !0, Tl(e, t, n), JS = s, YS = c;
					break;
				case 0:
				case 11:
				case 14:
				case 15:
					kc(wx, n, t), LS || Dc(n, t, Tx), Tl(e, t, n);
					break;
				case 1:
					LS || (Rc(n, t), s = n.stateNode, typeof s.componentWillUnmount == "function" && Fc(n, t, s)), Tl(e, t, n);
					break;
				case 21:
					Tl(e, t, n);
					break;
				case 22:
					LS = (s = LS) || n.memoizedState !== null, Tl(e, t, n), LS = s;
					break;
				case 30:
					n.flags & 18874368 && gl(n), Rc(n, t), Tl(e, t, n);
					break;
				case 7:
					LS || Rc(n, t), Tl(e, t, n);
					break;
				default: Tl(e, t, n);
			}
			(n.mode & W) !== U && 0 <= K && 0 <= q && (My || .05 < Ay) && wr(n, K, q, Ay, jy), Bi(r), Hi(i), jy = a, My = o;
		}
		function Dl(e, t) {
			if (t.memoizedState === null && (e = t.alternate, e !== null && (e = e.memoizedState, e !== null))) {
				e = e.dehydrated;
				try {
					w(t, fp, e);
				} catch (e) {
					Ku(t, t.return, e);
				}
			}
		}
		function Ol(e, t) {
			if (t.memoizedState === null && (e = t.alternate, e !== null && (e = e.memoizedState, e !== null && (e = e.dehydrated, e !== null)))) try {
				w(t, pp, e);
			} catch (e) {
				Ku(t, t.return, e);
			}
		}
		function kl(e) {
			switch (e.tag) {
				case 31:
				case 13:
				case 19:
					var t = e.stateNode;
					return t === null && (t = e.stateNode = new BS()), t;
				case 22: return e = e.stateNode, t = e._retryCache, t === null && (t = e._retryCache = new BS()), t;
				default: throw Error("Unexpected Suspense handler tag (" + e.tag + "). This is a bug in React.");
			}
		}
		function Al(e, t) {
			var n = kl(e);
			t.forEach(function(t) {
				if (!n.has(t)) {
					if (n.add(t), Ph) {
						if (HS !== null && US !== null) nd(US, HS);
						else throw Error("Expected finished root and lanes to be set. This is a bug in React.");
					}
					var r = Zu.bind(null, e, t);
					t.then(r, r);
				}
			});
		}
		function j(e, t, n) {
			var r = t.deletions;
			if (r !== null) for (var i = 0; i < r.length; i++) {
				var a = e, o = t, s = r[i], c = zi(), l = o;
				a: for (; l !== null;) {
					switch (l.tag) {
						case 27:
							if (ff(l.type)) {
								JS = l.stateNode, YS = !1;
								break a;
							}
							break;
						case 5:
							JS = l.stateNode, YS = !1;
							break a;
						case 3:
						case 4:
							JS = l.stateNode.containerInfo, YS = !0;
							break a;
					}
					l = l.return;
				}
				if (JS === null) throw Error("Expected to find a host parent. This error is likely caused by a bug in React. Please file an issue.");
				El(a, o, s), JS = null, YS = !1, (s.mode & W) !== U && 0 <= K && 0 <= q && .05 < q - K && br(s, K, q, "Unmount"), Bi(c), a = s, o = a.alternate, o !== null && (o.return = null), a.return = null;
			}
			if (t.subtreeFlags & 13886) for (t = t.child; t !== null;) jl(t, e, n), t = t.sibling;
		}
		function jl(e, t, n) {
			var r = zi(), i = Vi(), a = Ui(), o = Wi(), s = e.alternate, c = e.flags;
			switch (e.tag) {
				case 0:
				case 11:
				case 14:
				case 15:
					if (c & 4 && (s = e.updateQueue, s = s === null ? null : s.events, s !== null)) for (var l = 0; l < s.length; l++) {
						var u = s[l];
						u.ref.impl = u.nextImpl;
					}
					j(t, e, n), Ml(e), c & 4 && (kc(wx | Cx, e, e.return), Oc(wx | Cx, e), Dc(e, e.return, Tx | Cx));
					break;
				case 1:
					j(t, e, n), Ml(e), c & 512 && (LS || s === null || Rc(s, s.return)), c & 64 && IS && (t = e.updateQueue, t !== null && (n = t.callbacks, n !== null && (c = t.shared.hiddenCallbacks, t.shared.hiddenCallbacks = c === null ? n : c.concat(n))));
					break;
				case 26:
					if (l = XS, j(t, e, n), Ml(e), c & 512 && (LS || s === null || Rc(s, s.return)), c & 4) {
						if (c = s === null ? null : s.memoizedState, n = e.memoizedState, s === null) {
							if (n === null) {
								if (e.stateNode === null) {
									if (IS) e.stateNode = Qd(e.type, e.memoizedProps, t.containerInfo, e);
									else {
										a: {
											t = e.type, n = e.memoizedProps, c = l.ownerDocument || l;
											b: switch (t) {
												case "title":
													s = c.getElementsByTagName("title")[0], (!s || s[$h] || s[Kh] || s.namespaceURI === Lg || s.hasAttribute("itemprop")) && (s = c.createElement(t), c.head.insertBefore(s, c.querySelector("head > title"))), Fd(s, t, n), s[Kh] = e, _t(s), t = s;
													break a;
												case "link":
													if (l = Pp("link", "href", c).get(t + (n.href || ""))) {
														for (u = 0; u < l.length; u++) if (s = l[u], s.getAttribute("href") === (n.href == null || n.href === "" ? null : n.href) && s.getAttribute("rel") === (n.rel == null ? null : n.rel) && s.getAttribute("title") === (n.title == null ? null : n.title) && s.getAttribute("crossorigin") === (n.crossOrigin == null ? null : n.crossOrigin)) {
															l.splice(u, 1);
															break b;
														}
													}
													s = c.createElement(t), Fd(s, t, n), c.head.appendChild(s);
													break;
												case "meta":
													if (l = Pp("meta", "content", c).get(t + (n.content || ""))) {
														for (u = 0; u < l.length; u++) if (s = l[u], He(n.content, "content"), s.getAttribute("content") === (n.content == null ? null : "" + n.content) && s.getAttribute("name") === (n.name == null ? null : n.name) && s.getAttribute("property") === (n.property == null ? null : n.property) && s.getAttribute("http-equiv") === (n.httpEquiv == null ? null : n.httpEquiv) && s.getAttribute("charset") === (n.charSet == null ? null : n.charSet)) {
															l.splice(u, 1);
															break b;
														}
													}
													s = c.createElement(t), Fd(s, t, n), c.head.appendChild(s);
													break;
												default: throw Error("getNodesForType encountered a type it did not expect: \"" + t + "\". This is a bug in React.");
											}
											s[Kh] = e, _t(s), t = s;
										}
										e.stateNode = t;
									}
								} else IS || Fp(l, e.type, e.stateNode);
							} else e.stateNode = Ap(l, n, e.memoizedProps);
						} else c === n ? n === null && e.stateNode !== null && qc(e, e.memoizedProps, s.memoizedProps) : (c === null ? (t = s.stateNode, t === null || LS || t.parentNode.removeChild(t)) : c.count--, n === null ? IS || Fp(l, e.type, e.stateNode) : Ap(l, n, e.memoizedProps));
					}
					break;
				case 27:
					j(t, e, n), Ml(e), c & 512 && (LS || s === null || Rc(s, s.return)), s !== null && c & 4 && qc(e, e.memoizedProps, s.memoizedProps);
					break;
				case 5:
					if (l = RS, RS = !1, j(t, e, n), RS = l, Ml(e), c & 512 && (LS || s === null || Rc(s, s.return)), e.flags & 32) {
						t = e.stateNode;
						try {
							w(e, lf, t), B = !0;
						} catch (t) {
							Ku(e, e.return, t);
						}
					}
					c & 4 && e.stateNode != null && (t = e.memoizedProps, qc(e, t, s === null ? t : s.memoizedProps)), c & 1024 && (zS = !0, e.type !== "form" && console.error("Unexpected host component type. Expected a form. This is a bug in React."));
					break;
				case 6:
					if (j(t, e, n), Ml(e), c & 4) {
						if (e.stateNode === null) throw Error("This should have a text node initialized. This error is likely caused by a bug in React. Please file an issue.");
						t = e.memoizedProps, n = s === null ? t : s.memoizedProps, c = e.stateNode;
						try {
							w(e, uf, c, n, t), B = !0;
						} catch (t) {
							Ku(e, e.return, t);
						}
					}
					break;
				case 3:
					if (l = Fi(), B = !1, GT = null, u = XS, XS = bp(t.containerInfo), j(t, e, n), XS = u, Ml(e), c & 4 && s !== null && s.memoizedState.isDehydrated) try {
						w(e, dp, t.containerInfo);
					} catch (t) {
						Ku(e, e.return, t);
					}
					zS && (zS = !1, Nl(e)), t.effectDuration += Ii(l), B = !1;
					break;
				case 4:
					c = RS, RS = IS, s = Ct(), l = XS, XS = bp(e.stateNode.containerInfo), j(t, e, n), Ml(e), XS = l, B && GS && (KS = !0), B = s, RS = c;
					break;
				case 12:
					c = Fi(), j(t, e, n), Ml(e), e.stateNode.effectDuration += Li(c);
					break;
				case 31:
					j(t, e, n), Ml(e), c & 4 && (t = e.updateQueue, t !== null && (e.updateQueue = null, Al(e, t)));
					break;
				case 13:
					j(t, e, n), Ml(e), e.child.flags & 8192 && e.memoizedState !== null != (s !== null && s.memoizedState !== null) && (zC = Sh()), c & 4 && (t = e.updateQueue, t !== null && (e.updateQueue = null, Al(e, t)));
					break;
				case 22:
					l = e.memoizedState !== null, u = s !== null && s.memoizedState !== null;
					var d = IS, f = LS, p = RS;
					IS = d || l, RS = p || l, LS = f || u, j(t, e, n), LS = f, RS = p, IS = d, u && !l && !d && !f && (e.mode & W) !== U && 0 <= K && 0 <= q && .05 < q - K && xr(e, K, q), Ml(e), c & 8192 && (t = e.stateNode, t._visibility = l ? t._visibility & ~Nv : t._visibility | Nv, !l || s === null || u || IS || LS || (t = FS, n = u || LS, s = IS, u = LS, IS = l || IS, LS = n, Rl(e, t), (e.mode & W) !== U && 0 <= K && 0 <= q && .05 < q - K && br(e, K, q, "Disconnect"), IS = s, LS = u), !l && RS || xl(e, l)), c & 4 && (t = e.updateQueue, t !== null && (n = t.retryQueue, n !== null && (t.retryQueue = null, Al(e, n))));
					break;
				case 19:
					j(t, e, n), Ml(e), c & 4 && (t = e.updateQueue, t !== null && (e.updateQueue = null, Al(e, t)));
					break;
				case 30:
					c & 512 && (LS || s === null || Rc(s, s.return)), c = Ct(), l = GS, u = (n & 335544064) === n, d = e.memoizedProps, GS = u && pr(d.default, d.update) !== "none", j(t, e, n), Ml(e), u && s !== null && B && (e.flags |= 4), GS = l, B = c;
					break;
				case 21: break;
				case 7: c & 512 && (LS || s === null || Rc(s, s.return)), s && s.stateNode !== null && (s.stateNode._fragmentFiber = e);
				default: j(t, e, n), Ml(e);
			}
			(e.mode & W) !== U && 0 <= K && 0 <= q && ((My || .05 < Ay) && wr(e, K, q, Ay, jy), e.alternate === null && e.return !== null && e.return.alternate !== null && .05 < q - K && (_l(e.return.alternate, e.return) || br(e, K, q, "Mount"))), Bi(r), Hi(i), jy = a, My = o;
		}
		function Ml(e) {
			var t = e.flags;
			if (t & 2) {
				try {
					w(e, Qc, e);
				} catch (t) {
					Ku(e, e.return, t);
				}
				e.flags &= -3;
			}
			t & 4096 && (e.flags &= -4097);
		}
		function Nl(e) {
			if (e.subtreeFlags & 1024) for (e = e.child; e !== null;) {
				var t = e;
				Nl(t), t.tag === 5 && t.flags & 1024 && (t = t.stateNode, gE = !0, t.reset(), gE = !1), e = e.sibling;
			}
		}
		function Pl(e, t) {
			if (t.subtreeFlags & 9270) for (t = t.child; t !== null;) Fl(t, e), t = t.sibling;
			else ml(t, !1);
		}
		function Fl(e, t) {
			var n = e.alternate;
			if (n === null) ol(e, !1);
			else switch (e.tag) {
				case 3:
					if (qS = WS = !1, tl(), Pl(t, e), !WS && !KS) {
						if (e = kS, e !== null) for (var r = 0; r < e.length; r += 3) {
							n = e[r];
							var i = e[r + 1];
							Tf(n, e[r + 2]), n = n.ownerDocument.documentElement, n !== null && n.animate({
								opacity: [0, 0],
								pointerEvents: ["none", "none"]
							}, {
								duration: 0,
								fill: "forwards",
								pseudoElement: "::view-transition-group(" + i + ")"
							});
						}
						e = t.containerInfo, e = e.nodeType === 9 ? e.documentElement : e.ownerDocument.documentElement, e !== null && e.style.viewTransitionName === "" && (e.style.viewTransitionName = "none", e.animate({
							opacity: [0, 0],
							pointerEvents: ["none", "none"]
						}, {
							duration: 0,
							fill: "forwards",
							pseudoElement: "::view-transition-group(root)"
						}), e.animate({
							width: [0, 0],
							height: [0, 0]
						}, {
							duration: 0,
							fill: "forwards",
							pseudoElement: "::view-transition"
						})), qS = !0;
					}
					kS = null;
					break;
				case 5:
					Pl(t, e);
					break;
				case 4:
					r = WS, WS = !1, Pl(t, e), WS && (KS = !0), WS = r;
					break;
				case 22:
					e.memoizedState === null && (n.memoizedState === null ? Pl(t, e) : ol(e, !1));
					break;
				case 30:
					r = WS, i = tl(), WS = !1, Pl(t, e), WS && (e.flags |= 4);
					var a = e.memoizedProps, o = e.stateNode;
					t = dr(a, o), o = dr(n.memoizedProps, o);
					var s = pr(a.default, a.update);
					s === "none" ? t = !1 : (a = n.memoizedState, n.memoizedState = null, n = e.child, AS = 0, t = pl(e, n, t, o, s, a, !0), AS !== (a === null ? 0 : a.length) && (e.flags |= 32)), e.flags & 4 && t ? (ou(e, e.memoizedProps.onUpdate), kS = i) : i !== null && (i.push.apply(i, kS), kS = i), WS = e.flags & 32 ? !0 : r;
					break;
				default: Pl(t, e);
			}
		}
		function Il(e, t) {
			if (t.subtreeFlags & 8772) for (t = t.child; t !== null;) bl(e, t.alternate, t), t = t.sibling;
		}
		function Ll(e, t) {
			var n = zi(), r = Vi(), i = Ui(), a = Wi();
			switch (e.tag) {
				case 0:
				case 11:
				case 14:
				case 15:
					Dc(e, e.return, Tx), Rl(e, t);
					break;
				case 1:
					Rc(e, e.return);
					var o = e.stateNode;
					typeof o.componentWillUnmount == "function" && Fc(e, e.return, o), Rl(e, t);
					break;
				case 27: (t & FS) !== NS && w(e, vp, e.stateNode, e.type, e.memoizedProps);
				case 5:
					Rc(e, e.return), e.tag !== 5 && e.tag !== 27 || Uc(e), Rl(e, t);
					break;
				case 6:
					Uc(e);
					break;
				case 26:
					Rc(e, e.return), o = e.stateNode, e.memoizedState !== null || o === null || LS || o.parentNode.removeChild(o), Rl(e, t);
					break;
				case 22:
					e.memoizedState === null && Rl(e, t);
					break;
				case 30:
					e.flags & 18874368 && gl(e), Rc(e, e.return), Rl(e, t);
					break;
				case 7: Rc(e, e.return);
				default: Rl(e, t);
			}
			(e.mode & W) !== U && 0 <= K && 0 <= q && (My || .05 < Ay) && wr(e, K, q, Ay, jy), Bi(n), Hi(r), jy = i, My = a;
		}
		function Rl(e, t) {
			for (e = e.child; e !== null;) Ll(e, t), e = e.sibling;
		}
		function zl(e, t, n, r) {
			var i = zi(), a = Vi(), o = Ui(), s = Wi(), c = n.flags, l = (r & PS) !== NS;
			switch (n.tag) {
				case 0:
				case 11:
				case 15:
					Bl(e, n, r), Ec(n, Tx);
					break;
				case 1:
					if (Bl(e, n, r), t = n.stateNode, typeof t.componentDidMount == "function" && w(n, Ab, n, t), t = n.updateQueue, t !== null) {
						e = n.stateNode;
						try {
							w(n, Ma, t, e);
						} catch (e) {
							Ku(n, n.return, e);
						}
					}
					l && c & 64 && Mc(n), Lc(n, n.return);
					break;
				case 27: (r & FS) !== NS && $c(n);
				case 5:
					n.tag !== 5 && n.tag !== 27 || Hc(n), Bl(e, n, r), l && t === null && c & 4 && Kc(n), Lc(n, n.return);
					break;
				case 6:
					Hc(n);
					break;
				case 26:
					var u = n.stateNode;
					n.memoizedState !== null || u === null || IS || Fp(bp(u.ownerDocument), n.type, u), Bl(e, n, r), l && t === null && c & 4 && Kc(n), Lc(n, n.return);
					break;
				case 12:
					if (l && c & 4) {
						c = Fi(), Bl(e, n, r), l = n.stateNode, l.effectDuration += Li(c);
						try {
							w(n, zc, n, t, Ty, l.effectDuration);
						} catch (e) {
							Ku(n, n.return, e);
						}
					} else Bl(e, n, r);
					break;
				case 31:
					Bl(e, n, r), l && c & 4 && Dl(e, n);
					break;
				case 13:
					Bl(e, n, r), l && c & 4 && Ol(e, n);
					break;
				case 22:
					n.memoizedState === null && Bl(e, n, r), Lc(n, n.return);
					break;
				case 30:
					Bl(e, n, r), c & 18874368 && hl(n), Lc(n, n.return);
					break;
				case 7: Lc(n, n.return);
				default: Bl(e, n, r);
			}
			(n.mode & W) !== U && 0 <= K && 0 <= q && (My || .05 < Ay) && wr(n, K, q, Ay, jy), Bi(i), Hi(a), jy = o, My = s;
		}
		function Bl(e, t, n) {
			for (n = t.subtreeFlags & 8772 ? n : n & ~PS, t = t.child; t !== null;) zl(e, t.alternate, t, n), t = t.sibling;
		}
		function Vl(e, t) {
			var n = null;
			e !== null && e.memoizedState !== null && e.memoizedState.cachePool !== null && (n = e.memoizedState.cachePool.pool), e = null, t.memoizedState !== null && t.memoizedState.cachePool !== null && (e = t.memoizedState.cachePool.pool), e !== n && (e != null && ki(e), n != null && Ai(n));
		}
		function Hl(e, t) {
			e = null, t.alternate !== null && (e = t.alternate.memoizedState.cache), t = t.memoizedState.cache, t !== e && (ki(t), e != null && Ai(e));
		}
		function Ul(e, t, n, r, i) {
			var a = (n & 335544064) === n;
			if (t.subtreeFlags & (a ? 10262 : 10256) || t.actualDuration !== 0 && (t.alternate === null || t.alternate.child !== t.child)) for (t = t.child; t !== null;) a = t.sibling, Wl(e, t, n, r, a === null ? i : a.actualStartTime), t = a;
			else a && fl(t);
		}
		function Wl(e, t, n, r, i) {
			var a = zi(), o = Vi(), s = Ui(), c = Wi(), l = Dv, u = (n & 335544064) === n;
			u && t.alternate === null && t.return !== null && t.return.alternate !== null && dl(t);
			var d = t.flags;
			switch (t.tag) {
				case 0:
				case 11:
				case 15:
					(t.mode & W) !== U && 0 < t.actualStartTime && t.flags & 1 && Sr(t, t.actualStartTime, i, ZS, n), Ul(e, t, n, r, i), d & 2048 && Ac(t, Ex | Cx);
					break;
				case 1:
					(t.mode & W) !== U && 0 < t.actualStartTime && (t.flags & 128 ? Cr(t, t.actualStartTime, i, []) : t.flags & 1 && Sr(t, t.actualStartTime, i, ZS, n)), Ul(e, t, n, r, i);
					break;
				case 3:
					var f = Fi(), p = ZS;
					ZS = t.alternate !== null && t.alternate.memoizedState.isDehydrated && !(t.flags & 256), Ul(e, t, n, r, i), ZS = p, u && qS && (n = e.containerInfo, n = n.nodeType === 9 ? n.body : n.nodeName === "HTML" ? n.ownerDocument.body : n, n.style.viewTransitionName === "root" && (n.style.viewTransitionName = ""), n = n.ownerDocument.documentElement, n !== null && n.style.viewTransitionName === "none" && (n.style.viewTransitionName = "")), d & 2048 && (n = null, t.alternate !== null && (n = t.alternate.memoizedState.cache), r = t.memoizedState.cache, r !== n && (ki(r), n != null && Ai(n))), e.passiveEffectDuration += Ii(f);
					break;
				case 12:
					if (d & 2048) {
						d = Fi(), Ul(e, t, n, r, i), e = t.stateNode, e.passiveEffectDuration += Li(d);
						try {
							w(t, Bc, t, t.alternate, Ty, e.passiveEffectDuration);
						} catch (e) {
							Ku(t, t.return, e);
						}
					} else Ul(e, t, n, r, i);
					break;
				case 31:
					d = ZS, f = t.alternate === null ? null : t.alternate.memoizedState, u = t.memoizedState, f !== null && u === null ? (u = t.deletions, u !== null && 0 < u.length && u[0].tag === 18 ? (ZS = !1, f = f.hydrationErrors, f !== null && Cr(t, t.actualStartTime, i, f)) : ZS = !0) : ZS = !1, Ul(e, t, n, r, i), ZS = d;
					break;
				case 13:
					d = ZS, f = t.alternate === null ? null : t.alternate.memoizedState, u = t.memoizedState, f === null || f.dehydrated === null || u !== null && u.dehydrated !== null ? ZS = !1 : (u = t.deletions, u !== null && 0 < u.length && u[0].tag === 18 ? (ZS = !1, f = f.hydrationErrors, f !== null && Cr(t, t.actualStartTime, i, f)) : ZS = !0), Ul(e, t, n, r, i), ZS = d;
					break;
				case 23: break;
				case 22:
					p = t.stateNode, f = t.alternate, t.memoizedState === null ? (u && f !== null && f.memoizedState !== null && dl(t), p._visibility & Pv ? Ul(e, t, n, r, i) : (p._visibility |= Pv, Gl(e, t, n, r, !!(t.subtreeFlags & 10256) || t.actualDuration !== 0 && (t.alternate === null || t.alternate.child !== t.child), i), (t.mode & W) === U || ZS || (e = t.actualStartTime, 0 <= e && .05 < i - e && xr(t, e, i), 0 <= K && 0 <= q && .05 < q - K && xr(t, K, q)))) : (u && f !== null && f.memoizedState === null && dl(f), p._visibility & Pv ? Ul(e, t, n, r, i) : ql(e, t, n, r, i)), d & 2048 && Vl(f, t);
					break;
				case 24:
					Ul(e, t, n, r, i), d & 2048 && Hl(t.alternate, t);
					break;
				case 30:
					u && (d = t.alternate, d !== null && (il(d.child, !0), il(t.child, !0))), Ul(e, t, n, r, i);
					break;
				default: Ul(e, t, n, r, i);
			}
			(t.mode & W) !== U && ((e = !ZS && t.alternate === null && t.return !== null && t.return.alternate !== null) && (n = t.actualStartTime, 0 <= n && .05 < i - n && br(t, n, i, "Mount")), 0 <= K && 0 <= q && ((My || .05 < Ay) && wr(t, K, q, Ay, jy), e && .05 < q - K && br(t, K, q, "Mount"))), Bi(a), Hi(o), jy = s, My = c, Dv = l;
		}
		function Gl(e, t, n, r, i, a) {
			for (i &&= !!(t.subtreeFlags & 10256) || t.actualDuration !== 0 && (t.alternate === null || t.alternate.child !== t.child), t = t.child; t !== null;) {
				var o = t.sibling;
				Kl(e, t, n, r, i, o === null ? a : o.actualStartTime), t = o;
			}
		}
		function Kl(e, t, n, r, i, a) {
			var o = zi(), s = Vi(), c = Ui(), l = Wi(), u = Dv;
			i && (t.mode & W) !== U && 0 < t.actualStartTime && t.flags & 1 && Sr(t, t.actualStartTime, a, ZS, n);
			var d = t.flags;
			switch (t.tag) {
				case 0:
				case 11:
				case 15:
					Gl(e, t, n, r, i, a), Ac(t, Ex);
					break;
				case 23: break;
				case 22:
					var f = t.stateNode;
					t.memoizedState === null ? (f._visibility |= Pv, Gl(e, t, n, r, i, a)) : f._visibility & Pv ? Gl(e, t, n, r, i, a) : ql(e, t, n, r, a), i && d & 2048 && Vl(t.alternate, t);
					break;
				case 24:
					Gl(e, t, n, r, i, a), i && d & 2048 && Hl(t.alternate, t);
					break;
				default: Gl(e, t, n, r, i, a);
			}
			(t.mode & W) !== U && 0 <= K && 0 <= q && (My || .05 < Ay) && wr(t, K, q, Ay, jy), Bi(o), Hi(s), jy = c, My = l, Dv = u;
		}
		function ql(e, t, n, r, i) {
			if (t.subtreeFlags & 10256 || t.actualDuration !== 0 && (t.alternate === null || t.alternate.child !== t.child)) for (var a = t.child; a !== null;) {
				t = a.sibling;
				var o = e, s = n, c = r, l = t === null ? i : t.actualStartTime, u = Dv;
				(a.mode & W) !== U && 0 < a.actualStartTime && a.flags & 1 && Sr(a, a.actualStartTime, l, ZS, s);
				var d = a.flags;
				switch (a.tag) {
					case 22:
						ql(o, a, s, c, l), d & 2048 && Vl(a.alternate, a);
						break;
					case 24:
						ql(o, a, s, c, l), d & 2048 && Hl(a.alternate, a);
						break;
					default: ql(o, a, s, c, l);
				}
				Dv = u, a = t;
			}
		}
		function Jl(e, t, n) {
			if (e.subtreeFlags & QS) for (e = e.child; e !== null;) Yl(e, t, n), e = e.sibling;
		}
		function Yl(e, t, n) {
			switch (e.tag) {
				case 26:
					Jl(e, t, n), e.flags & QS && (e.memoizedState === null ? (e = e.stateNode, (t & 335544128) === t && Bp(n, e)) : Vp(n, XS, e.memoizedState, e.memoizedProps));
					break;
				case 5:
					Jl(e, t, n), e.flags & QS && (e = e.stateNode, (t & 335544128) === t && Bp(n, e));
					break;
				case 3:
				case 4:
					var r = XS;
					XS = bp(e.stateNode.containerInfo), Jl(e, t, n), XS = r;
					break;
				case 22:
					e.memoizedState === null && (r = e.alternate, r !== null && r.memoizedState !== null ? (r = QS, QS = 16777216, Jl(e, t, n), QS = r) : Jl(e, t, n));
					break;
				case 30:
					if ((e.flags & QS) !== 0 && (r = e.memoizedProps.name, r != null && r !== "auto")) {
						var i = e.stateNode;
						i.paired = null, OS === null && (OS = /* @__PURE__ */ new Map()), OS.set(r, i);
					}
					Jl(e, t, n);
					break;
				default: Jl(e, t, n);
			}
		}
		function Xl(e) {
			var t = e.alternate;
			if (t !== null && (e = t.child, e !== null)) {
				t.child = null;
				do
					t = e.sibling, e.sibling = null, e = t;
				while (e !== null);
			}
		}
		function Zl(e) {
			var t = e.deletions;
			if (e.flags & 16) {
				if (t !== null) for (var n = 0; n < t.length; n++) {
					var r = t[n], i = zi();
					VS = r, tu(r, e), (r.mode & W) !== U && 0 <= K && 0 <= q && .05 < q - K && br(r, K, q, "Unmount"), Bi(i);
				}
				Xl(e);
			}
			if (e.subtreeFlags & 10256) for (e = e.child; e !== null;) Ql(e), e = e.sibling;
		}
		function Ql(e) {
			var t = zi(), n = Vi(), r = Ui(), i = Wi();
			switch (e.tag) {
				case 0:
				case 11:
				case 15:
					Zl(e), e.flags & 2048 && jc(e, e.return, Ex | Cx);
					break;
				case 3:
					var a = Fi();
					Zl(e), e.stateNode.passiveEffectDuration += Ii(a);
					break;
				case 12:
					a = Fi(), Zl(e), e.stateNode.passiveEffectDuration += Li(a);
					break;
				case 22:
					a = e.stateNode, e.memoizedState !== null && a._visibility & Pv && (e.return === null || e.return.tag !== 13) ? (a._visibility &= ~Pv, $l(e), (e.mode & W) !== U && 0 <= K && 0 <= q && .05 < q - K && br(e, K, q, "Disconnect")) : Zl(e);
					break;
				default: Zl(e);
			}
			(e.mode & W) !== U && 0 <= K && 0 <= q && (My || .05 < Ay) && wr(e, K, q, Ay, jy), Bi(t), Hi(n), My = i, jy = r;
		}
		function $l(e) {
			var t = e.deletions;
			if (e.flags & 16) {
				if (t !== null) for (var n = 0; n < t.length; n++) {
					var r = t[n], i = zi();
					VS = r, tu(r, e), (r.mode & W) !== U && 0 <= K && 0 <= q && .05 < q - K && br(r, K, q, "Unmount"), Bi(i);
				}
				Xl(e);
			}
			for (e = e.child; e !== null;) eu(e), e = e.sibling;
		}
		function eu(e) {
			var t = zi(), n = Vi(), r = Ui(), i = Wi();
			switch (e.tag) {
				case 0:
				case 11:
				case 15:
					jc(e, e.return, Ex), $l(e);
					break;
				case 22:
					var a = e.stateNode;
					a._visibility & Pv && (a._visibility &= ~Pv, $l(e));
					break;
				default: $l(e);
			}
			(e.mode & W) !== U && 0 <= K && 0 <= q && (My || .05 < Ay) && wr(e, K, q, Ay, jy), Bi(t), Hi(n), My = i, jy = r;
		}
		function tu(e, t) {
			for (; VS !== null;) {
				var n = VS, r = n, i = t, a = zi(), o = Vi(), s = Ui(), c = Wi();
				switch (r.tag) {
					case 0:
					case 11:
					case 15:
						jc(r, i, Ex);
						break;
					case 23:
					case 22:
						r.memoizedState !== null && r.memoizedState.cachePool !== null && (i = r.memoizedState.cachePool.pool, i != null && ki(i));
						break;
					case 24: Ai(r.memoizedState.cache);
				}
				if ((r.mode & W) !== U && 0 <= K && 0 <= q && (My || .05 < Ay) && wr(r, K, q, Ay, jy), Bi(a), Hi(o), My = c, jy = s, r = n.child, r !== null) r.return = n, VS = r;
				else a: for (n = e; VS !== null;) {
					if (r = VS, a = r.sibling, o = r.return, wl(r), r === n) {
						VS = null;
						break a;
					}
					if (a !== null) {
						a.return = o, VS = a;
						break a;
					}
					VS = o;
				}
			}
		}
		function nu() {
			tC.forEach(function(e) {
				return e();
			});
		}
		function ru() {
			var e = typeof IS_REACT_ACT_ENVIRONMENT < "u" ? IS_REACT_ACT_ENVIRONMENT : void 0;
			return e || R.actQueue === null || console.error("The current testing environment is not configured to support act(...)"), e;
		}
		function iu(e) {
			if ((Z & iC) !== rC && $ !== 0) return $ & -$;
			var t = R.T;
			return t === null ? (e = ut(), e === Vh && (qb = null), e) : (t._updatedFibers ||= /* @__PURE__ */ new Set(), t._updatedFibers.add(e), qb !== null && ut() === Vh && (qb = null), md());
		}
		function au() {
			if (PC === 0) {
				if (!($ & 536870912) || G) {
					var e = zh;
					zh <<= 1, !(zh & 3932160) && (zh = 262144), PC = e;
				} else PC = 536870912;
			}
			return e = _x.current, e !== null && (e.flags |= 32), PC;
		}
		function ou(e, t) {
			if (t != null) {
				var n = e.stateNode, r = n.ref;
				r === null && (r = n.ref = Pf(dr(e.memoizedProps, n))), fw === null && (fw = []), fw.push(t.bind(null, r));
			}
		}
		function su(e, t, n) {
			if (Tw && console.error("useInsertionEffect must not schedule updates."), bw && (xw = !0), (e === pC && (wC === gC || wC === CC) || e.cancelPendingCommit !== null) && (hu(e, 0), du(e, $, PC, !1)), tt(e, n), (Z & iC) !== rC && e === pC) {
				if (gh) switch (t.tag) {
					case 0:
					case 11:
					case 15:
						e = Q && S(Q) || "Unknown", kw.has(e) || (kw.add(e), t = S(t) || "Unknown", console.error("Cannot update a component (`%s`) while rendering a different component (`%s`). To locate the bad setState() call inside `%s`, follow the stack trace as described in https://react.dev/link/setstate-in-render", t, e, e));
						break;
					case 1: Ow ||= (console.error("Cannot update during an existing state transition (such as within `render`). Render methods should be a pure function of props and state."), !0);
				}
			} else Ph && st(e, t, n), id(t), e === pC && ((Z & iC) === rC && (MC |= n), AC === uC && du(e, $, PC, !1)), ad(e);
		}
		function cu(e, t, n) {
			if ((Z & (iC | aC)) !== rC) throw Error("Should not already be working.");
			if ($ !== 0 && Q !== null) {
				var r = Q, i = Sh();
				switch (ib) {
					case _C:
					case gC:
						var a = ab;
						Tv && ((r = r._debugTask) ? r.run(console.timeStamp.bind(console, "Suspended", a, i, Ev, void 0, "primary-light")) : console.timeStamp("Suspended", a, i, Ev, void 0, "primary-light"));
						break;
					case CC:
						a = ab, Tv && ((r = r._debugTask) ? r.run(console.timeStamp.bind(console, "Action", a, i, Ev, void 0, "primary-light")) : console.timeStamp("Action", a, i, Ev, void 0, "primary-light"));
						break;
					default: Tv && (r = i - ab, 3 > r || console.timeStamp("Blocked", ab, i, Ev, void 0, 5 > r ? "primary-light" : 10 > r ? "primary" : 100 > r ? "primary-dark" : "error"));
				}
			}
			a = (n = !n && !(t & 127) && (t & e.expiredLanes) === 0 || Xe(e, t)) ? wu(e, t) : Su(e, t, !0);
			var o = n;
			do {
				if (a === oC) {
					DC && !n && du(e, t, 0, !1), t = wC, ab = by(), ib = t;
					break;
				}
				if (r = Sh(), i = e.current.alternate, o && !uu(i)) yr(t), i = wy, a = r, !Tv || a <= i || (GC ? GC.run(console.timeStamp.bind(console, "Teared Render", i, a, H, V, "error")) : console.timeStamp("Teared Render", i, a, H, V, "error")), mu(t, r), a = Su(e, t, !1), o = !1;
				else {
					if (a === cC) {
						if (o = t, e.errorRecoveryDisabledLanes & o) var s = 0;
						else s = e.pendingLanes & -536870913, s = s === 0 ? s & 536870912 ? 536870912 : 0 : s;
						if (s !== 0) {
							yr(t), Dr(wy, r, t, GC), mu(t, r), t = s;
							a: {
								r = e, a = o, o = IC;
								var c = r.current.memoizedState.isDehydrated;
								if (c && (hu(r, s).flags |= 256), s = Su(r, s, !1), s !== cC && s !== dC) {
									if (OC && !c) {
										r.errorRecoveryDisabledLanes |= a, MC |= a, a = uC;
										break a;
									}
									r = LC, LC = o, r !== null && (LC === null ? LC = r : LC.push.apply(LC, r));
								}
								a = s;
							}
							if (o = !1, a !== cC) continue;
							r = Sh();
						}
					}
					if (a === sC) {
						yr(t), Dr(wy, r, t, GC), mu(t, r), hu(e, 0), du(e, t, 0, !0);
						break;
					}
					a: {
						switch (n = e, a) {
							case oC:
							case sC: throw Error("Root did not complete. This is a bug in React.");
							case uC: if ((t & 4194048) !== t && (t & 62914560) !== t) break;
							case dC:
								yr(t), Tr(wy, r, t, GC), mu(t, r), i = t, i & 127 ? Hy = r : i & 4194048 && ($y = r), du(n, t, PC, !EC);
								break a;
							case cC:
								LC = null;
								break;
							case lC:
							case fC: break;
							default: throw Error("Unknown root exit status.");
						}
						if (R.actQueue !== null) Mu(n, i, t, LC, WC, RC, PC, MC, FC, EC, a, null, null, wy, r);
						else {
							if ((t & 62914560) === t && (o = zC + VC - Sh(), 10 < o)) {
								if (du(n, t, PC, !EC), Ye(n, 0, !0) !== 0) break a;
								ow = t, n.timeoutHandle = ET(lu.bind(null, n, i, LC, WC, RC, t, PC, MC, FC, EC, a, "Throttled", wy, r), o);
								break a;
							}
							lu(n, i, LC, WC, RC, t, PC, MC, FC, EC, a, null, wy, r);
						}
					}
					break;
				}
			} while (1);
			ad(e);
		}
		function lu(e, t, n, r, i, a, o, s, c, l, u, d, f, p) {
			e.timeoutHandle = OT;
			var m = t.subtreeFlags, h = (a & 335544064) === a, g = null;
			(h || m & 8192 || (m & 16785408) == 16785408) && (g = {
				stylesheets: null,
				count: 0,
				imgCount: 0,
				imgBytes: 0,
				suspenseyImages: [],
				waitingForImages: !0,
				waitingForViewTransition: !1,
				unsuspend: Tn
			}, OS = null, Yl(t, a, g), h && (m = g, h = e.containerInfo, h = (h.nodeType === 9 ? h : h.ownerDocument).__reactViewTransition, h != null && (m.count++, m.waitingForViewTransition = !0, m = Wp.bind(m), h.finished.then(m, m))), m = (a & 62914560) === a ? zC - Sh() : (a & 4194048) === a ? BC - Sh() : 0, m = Hp(g, m), m !== null) ? (ow = a, e.cancelPendingCommit = m(Mu.bind(null, e, t, a, n, r, i, o, s, c, l, u, g, g.waitingForViewTransition ? "Waiting for the previous Animation" : 0 < g.count ? 0 < g.imgCount ? "Suspended on CSS and Images" : "Suspended on CSS" : g.imgCount === 1 ? "Suspended on an Image" : 0 < g.imgCount ? "Suspended on Images" : null, f, p)), du(e, a, o, !l)) : Mu(e, t, a, n, r, i, o, s, c, l, u, g, d, f, p);
		}
		function uu(e) {
			for (var t = e;;) {
				var n = t.tag;
				if ((n === 0 || n === 11 || n === 15) && t.flags & 16384 && (n = t.updateQueue, n !== null && (n = n.stores, n !== null))) for (var r = 0; r < n.length; r++) {
					var i = n[r], a = i.getSnapshot;
					i = i.value;
					try {
						if (!K_(a(), i)) return !1;
					} catch {
						return !1;
					}
				}
				if (n = t.child, t.subtreeFlags & 16384 && n !== null) n.return = t, t = n;
				else {
					if (t === e) break;
					for (; t.sibling === null;) {
						if (t.return === null || t.return === e) return !0;
						t = t.return;
					}
					t.sibling.return = t.return, t = t.sibling;
				}
			}
			return !0;
		}
		function du(e, t, n, r) {
			t = Ze(e, t), t &= ~NC, t &= ~MC, e.suspendedLanes |= t, e.pingedLanes &= ~t, r && (e.warmLanes |= t), r = e.expirationTimes;
			for (var i = t; 0 < i;) {
				var a = 31 - Fh(i), o = 1 << a;
				r[a] = -1, i &= ~o;
			}
			n !== 0 && rt(e, n, t);
		}
		function fu() {
			return (Z & (iC | aC)) !== rC || (od(0, !1), !1);
		}
		function pu() {
			if (Q !== null) {
				if (wC === mC) var e = Q.return;
				else e = Q, _i(), to(e), Qb = null, $b = 0, e = Q;
				for (; e !== null;) wc(e.alternate, e), e = e.return;
				Q = null;
			}
		}
		function mu(e, t) {
			e & 127 && (Ny = t), e & 4194048 && (Uy = t), e & 62914560 && (eb = t), e & 2080374784 && (tb = t);
		}
		function hu(e, t) {
			Tv && (console.timeStamp("Blocking Track", .003, .003, "Blocking", V, "primary-light"), console.timeStamp("Transition Track", .003, .003, "Transition", V, "primary-light"), console.timeStamp("Suspense Track", .003, .003, "Suspense", V, "primary-light"), console.timeStamp("Idle Track", .003, .003, "Idle", V, "primary-light"));
			var n = wy;
			if (wy = by(), $ !== 0 && 0 < n) {
				if (yr($), AC === lC || AC === uC) Tr(n, wy, t, GC);
				else {
					var r = wy, i = GC;
					if (Tv && !(r <= n)) {
						var a = (t & 738197653) === t ? "tertiary-dark" : "primary-dark", o = (t & 536870912) === t ? "Prewarm" : (t & 201326741) === t ? "Interrupted Hydration" : "Interrupted Render";
						i ? i.run(console.timeStamp.bind(console, o, n, r, H, V, a)) : console.timeStamp(o, n, r, H, V, a);
					}
				}
				mu($, wy);
			}
			if (n = GC, GC = null, t & 127) {
				GC = Fy, i = 0 <= Py && Py < Ny ? Ny : Py, r = 0 <= zy && zy < Ny ? Ny : zy, a = 0 <= r ? r : 0 <= i ? i : wy, 0 <= Hy ? (yr(2), Er(Hy, a, t, n)) : nb & 127 && (yr(2), jr(Ny, a, rb)), n = i;
				var s = r, c = By, l = 0 < Vy, u = Iy === Sy, d = Iy === Cy;
				if (i = wy, r = Fy, a = Ly, o = Ry, Tv) {
					if (H = "Blocking", 0 < n ? n > i && (n = i) : n = i, 0 < s ? s > n && (s = n) : s = n, c !== null && n > s) {
						var f = l ? "secondary-light" : "warning";
						r ? r.run(console.timeStamp.bind(console, l ? "Consecutive" : "Event: " + c, s, n, H, V, f)) : console.timeStamp(l ? "Consecutive" : "Event: " + c, s, n, H, V, f);
					}
					i > n && (s = u ? "error" : (t & 738197653) === t ? "tertiary-light" : "primary-light", u = d ? "Promise Resolved" : u ? "Cascading Update" : 5 < i - n ? "Update Blocked" : "Update", d = [], o != null && d.push(["Component name", o]), a != null && d.push(["Method name", a]), n = {
						start: n,
						end: i,
						detail: { devtools: {
							properties: d,
							track: H,
							trackGroup: V,
							color: s
						} }
					}, r ? r.run(performance.measure.bind(performance, u, n)) : performance.measure(u, n), performance.clearMeasures(u));
				}
				Py = -1.1, Iy = 0, Ry = Ly = null, Hy = -1.1, Vy = zy, zy = -1.1, Ny = by();
			}
			return t & 4194048 && (GC = qy, i = 0 <= Wy && Wy < Uy ? Uy : Wy, n = 0 <= Gy && Gy < Uy ? Uy : Gy, r = 0 <= Xy && Xy < Uy ? Uy : Xy, a = 0 <= r ? r : 0 <= n ? n : wy, 0 <= $y ? (yr(256), Er($y, a, t, GC)) : nb & 4194048 && (yr(256), jr(Uy, a, rb)), d = r, s = Zy, c = 0 < Qy, l = Ky === Cy, a = wy, r = qy, o = Jy, u = Yy, Tv && (H = "Transition", 0 < n ? n > a && (n = a) : n = a, 0 < i ? i > n && (i = n) : i = n, 0 < d ? d > i && (d = i) : d = i, i > d && s !== null && (f = c ? "secondary-light" : "warning", r ? r.run(console.timeStamp.bind(console, c ? "Consecutive" : "Event: " + s, d, i, H, V, f)) : console.timeStamp(c ? "Consecutive" : "Event: " + s, d, i, H, V, f)), n > i && (r ? r.run(console.timeStamp.bind(console, "Action", i, n, H, V, "primary-dark")) : console.timeStamp("Action", i, n, H, V, "primary-dark")), a > n && (i = l ? "Promise Resolved" : 5 < a - n ? "Update Blocked" : "Update", d = [], u != null && d.push(["Component name", u]), o != null && d.push(["Method name", o]), n = {
				start: n,
				end: a,
				detail: { devtools: {
					properties: d,
					track: H,
					trackGroup: V,
					color: "primary-light"
				} }
			}, r ? r.run(performance.measure.bind(performance, i, n)) : performance.measure(i, n), performance.clearMeasures(i))), Gy = Wy = -1.1, Ky = 0, $y = -1.1, Qy = Xy, Xy = -1.1, Uy = by()), t & 62914560 && nb & 62914560 && (yr(4194304), jr(eb, wy, rb)), t & 2080374784 && nb & 2080374784 && (yr(268435456), jr(tb, wy, rb)), n = e.timeoutHandle, n !== OT && (e.timeoutHandle = OT, DT(n)), n = e.cancelPendingCommit, n !== null && (e.cancelPendingCommit = null, n()), ow = 0, pu(), pC = e, Q = n = Wr(e.current, null), $ = t, wC = mC, TC = null, EC = !1, DC = Xe(e, t), OC = !1, AC = oC, FC = PC = NC = MC = jC = 0, LC = IC = null, RC = !1, kC = Ze(e, t), Mr(), e = pv(), 1e3 < e - dv && (R.recentlyCreatedOwnerStacks = 0, dv = e), mb.discardPendingWarnings(), n;
		}
		function gu(e, t) {
			Y = null, R.H = Kx, R.getCurrentStack = null, gh = !1, hh = null, t === Ub || t === Gb ? (t = ca(), wC = _C) : t === Wb ? (t = ca(), wC = vC) : wC = t === mS ? SC : typeof t == "object" && t && typeof t.then == "function" ? bC : hC, TC = t;
			var n = Q;
			n === null ? (AC = sC, js(e, Qr(t, e.current))) : n.mode & W && Ki(n);
		}
		function _u() {
			var e = _x.current;
			return e === null ? !0 : ($ & 4194048) === $ ? vx === null : ($ & 62914560) === $ || $ & 536870912 ? e === vx : !1;
		}
		function vu() {
			var e = R.H;
			return R.H = Kx, e === null ? Kx : e;
		}
		function yu() {
			var e = R.A;
			return R.A = $S, e;
		}
		function bu(e) {
			GC === null && (GC = e._debugTask == null ? null : e._debugTask);
		}
		function xu() {
			AC = uC, EC || ($ & 4194048) !== $ && _x.current !== null || (DC = !0), !(jC & 134217727) && !(MC & 134217727) || pC === null || du(pC, $, PC, !1);
		}
		function Su(e, t, n) {
			var r = Z;
			Z |= iC;
			var i = vu(), a = yu();
			if (pC !== e || $ !== t) {
				if (Ph) {
					var o = e.memoizedUpdaters;
					0 < o.size && (nd(e, $), o.clear()), ct(e, t);
				}
				WC = null, hu(e, t);
			}
			t = !1, o = AC;
			a: do
				try {
					if (wC !== mC && Q !== null) {
						var s = Q, c = TC;
						switch (wC) {
							case SC:
								pu(), o = dC;
								break a;
							case _C:
							case gC:
							case CC:
							case bC:
								_x.current === null && (t = !0);
								var l = wC;
								if (wC = mC, TC = null, ku(e, s, c, l), n && DC) {
									o = oC;
									break a;
								}
								break;
							default: l = wC, wC = mC, TC = null, ku(e, s, c, l);
						}
					}
					Cu(), o = AC;
					break;
				} catch (t) {
					gu(e, t);
				}
			while (1);
			return t && e.shellSuspendCounter++, _i(), Z = r, R.H = i, R.A = a, Q === null && (pC = null, $ = 0, Mr()), o;
		}
		function Cu() {
			for (; Q !== null;) Eu(Q);
		}
		function wu(e, t) {
			var n = Z;
			Z |= iC;
			var r = vu(), i = yu();
			if (pC !== e || $ !== t) {
				if (Ph) {
					var a = e.memoizedUpdaters;
					0 < a.size && (nd(e, $), a.clear()), ct(e, t);
				}
				WC = null, HC = Sh() + UC, hu(e, t);
			} else DC = Xe(e, t);
			a: do
				try {
					if (wC !== mC && Q !== null) b: switch (t = Q, a = TC, wC) {
						case hC:
							wC = mC, TC = null, ku(e, t, a, hC);
							break;
						case gC:
						case CC:
							if (aa(a)) {
								wC = mC, TC = null, Du(t);
								break;
							}
							t = function() {
								wC !== gC && wC !== CC || pC !== e || (wC = xC), ad(e);
							}, a.then(t, t);
							break a;
						case _C:
							wC = xC;
							break a;
						case vC:
							wC = yC;
							break a;
						case xC:
							aa(a) ? (wC = mC, TC = null, Du(t)) : (wC = mC, TC = null, ku(e, t, a, xC));
							break;
						case yC:
							var o = null;
							switch (Q.tag) {
								case 26: o = Q.memoizedState;
								case 5:
								case 27:
									var s = Q;
									if (o ? Rp(o) : s.stateNode.complete) {
										wC = mC, TC = null;
										var c = s.sibling;
										if (c !== null) Q = c;
										else {
											var l = s.return;
											l === null ? Q = null : (Q = l, Au(l));
										}
										break b;
									}
									break;
								default: console.error("Unexpected type of fiber triggered a suspensey commit. This is a bug in React.");
							}
							wC = mC, TC = null, ku(e, t, a, yC);
							break;
						case bC:
							wC = mC, TC = null, ku(e, t, a, bC);
							break;
						case SC:
							pu(), AC = dC;
							break a;
						default: throw Error("Unexpected SuspendedReason. This is a bug in React.");
					}
					R.actQueue === null ? Tu() : Cu();
					break;
				} catch (t) {
					gu(e, t);
				}
			while (1);
			return _i(), R.H = r, R.A = i, Z = n, Q === null ? (pC = null, $ = 0, Mr(), AC) : oC;
		}
		function Tu() {
			for (; Q !== null && !bh();) Eu(Q);
		}
		function Eu(e) {
			var t = e.alternate;
			(e.mode & W) === U ? t = w(e, hc, t, e, kC) : (Gi(e), t = w(e, hc, t, e, kC), Ki(e)), e.memoizedProps = e.pendingProps, t === null ? Au(e) : Q = t;
		}
		function Du(e) {
			var t = w(e, Ou, e);
			e.memoizedProps = e.pendingProps, t === null ? Au(e) : Q = t;
		}
		function Ou(e) {
			var t = e.alternate, n = (e.mode & W) !== U;
			switch (n && Gi(e), e.tag) {
				case 15:
				case 0:
					t = Ys(t, e, e.pendingProps, e.type, void 0, $);
					break;
				case 11:
					t = Ys(t, e, e.pendingProps, e.type.render, e.ref, $);
					break;
				case 5:
					to(e);
					var r = e;
					r === ny && (G ? (di(r), r.tag === 5 && r.stateNode != null && (ry = r.stateNode)) : (di(r), G = !0));
				default: wc(t, e), e = Q = Gr(e, kC), t = hc(t, e, kC);
			}
			return n && Ki(e), t;
		}
		function ku(e, t, n, r) {
			_i(), to(t), Qb = null, $b = 0;
			var i = t.return;
			try {
				if (Is(e, i, t, n, $)) {
					AC = sC, js(e, Qr(n, e.current)), Q = null;
					return;
				}
			} catch (t) {
				if (i !== null) throw Q = i, t;
				AC = sC, js(e, Qr(n, e.current)), Q = null;
				return;
			}
			t.flags & 32768 ? (G || r === hC ? e = !0 : DC || $ & 536870912 ? e = !1 : (EC = e = !0, (r === gC || r === CC || r === _C || r === bC) && (r = _x.current, r !== null && r.tag === 13 && (r.flags |= 16384))), ju(t, e)) : Au(t);
		}
		function Au(e) {
			var t = e;
			do {
				if (t.flags & 32768) {
					ju(t, EC);
					return;
				}
				var n = t.alternate;
				if (e = t.return, Gi(t), n = w(t, Sc, n, t, kC), (t.mode & W) !== U && qi(t), n !== null) {
					Q = n;
					return;
				}
				if (t = t.sibling, t !== null) {
					Q = t;
					return;
				}
				Q = t = e;
			} while (t !== null);
			AC === oC && (AC = fC);
		}
		function ju(e, t) {
			do {
				var n = Cc(e.alternate, e);
				if (n !== null) {
					n.flags &= 32767, Q = n;
					return;
				}
				if ((e.mode & W) !== U) {
					qi(e), n = e.actualDuration;
					for (var r = e.child; r !== null;) n += r.actualDuration, r = r.sibling;
					e.actualDuration = n;
				}
				if (n = e.return, n !== null && (n.flags |= 32768, n.subtreeFlags = 0, n.deletions = null), !t && (e = e.sibling, e !== null)) {
					Q = e;
					return;
				}
				Q = e = n;
			} while (e !== null);
			AC = dC, Q = null;
		}
		function Mu(e, t, n, r, i, a, o, s, c, l, u, d, f, p, m) {
			e.cancelPendingCommit = null;
			do
				Uu();
			while (rw !== ZC);
			if (mb.flushLegacyContextWarning(), mb.flushPendingUnsafeLifecycleWarnings(), (Z & (iC | aC)) !== rC) throw Error("Should not already be working.");
			if (yr(n), u === cC) Dr(p, m, n, GC);
			else if (r !== null) {
				if (l = t !== null && t.alternate !== null && t.alternate.memoizedState.isDehydrated && !!(t.flags & 256), a = GC, Tv && !(m <= p)) {
					u = [];
					for (var h = 0; h < r.length; h++) {
						var g = r[h].value;
						u.push(["Recoverable Error", typeof g == "object" && g && typeof g.message == "string" ? String(g.message) : String(g)]);
					}
					p = {
						start: p,
						end: m,
						detail: { devtools: {
							color: "primary-dark",
							track: H,
							trackGroup: V,
							tooltipText: l ? "Hydration Failed" : "Recovered after Error",
							properties: u
						} }
					}, a ? a.run(performance.measure.bind(performance, "Recovered", p)) : performance.measure("Recovered", p), performance.clearMeasures("Recovered");
				}
			} else a = GC, !Tv || m <= p || (l = (n & 738197653) === n ? "tertiary-dark" : "primary-dark", u = (n & 536870912) === n ? "Prepared" : (n & 201326741) === n ? "Hydrated" : "Render", a ? a.run(console.timeStamp.bind(console, u, p, m, H, V, l)) : console.timeStamp(u, p, m, H, V, l));
			if (t !== null) {
				if (n === 0 && console.error("finishedLanes should not be empty during a commit. This is a bug in React."), t === e.current) throw Error("Cannot commit the same tree as before. This error is likely caused by a bug in React. Please file an issue.");
				e === pC && (Q = pC = null, $ = 0), aw = t, iw = e, ow = n, lw = i, uw = r, cw = m, mw = f, hw = qC, gw = null, Nu(e, t, n, o, s, c, d, f, m);
			}
		}
		function Nu(e, t, n, r, i, a, o, s, c) {
			var l = t.lanes | t.childLanes;
			if (sw = l, l |= Lv, nt(e, n, l, r, i, a), fw = null, (n & 335544064) === n ? (pw = Mi(e), r = 10262) : (pw = null, r = 10256), t.actualDuration !== 0 || (t.subtreeFlags & r) !== 0 || (t.flags & r) !== 0 ? (e.callbackNode = null, e.callbackPriority = 0, rd(Eh, function() {
				return TT = window.event, hw === qC && (hw = YC), Wu(), null;
			})) : (e.callbackNode = null, e.callbackPriority = 0), Dy = null, Ty = by(), s !== null && Or(c, Ty, s, GC), DS = !1, s = !!(t.flags & 13878), t.subtreeFlags & 13878 || s) {
				s = R.T, R.T = null, c = z.p, z.p = Vh, r = Z, Z |= aC;
				try {
					vl(e, t, n);
				} finally {
					Z = r, z.p = c, R.T = s;
				}
			}
			rw = QC, DS ? (nb |= n, rb = null, dw = Mf(o, e.containerInfo, pw, Ru, zu, Lu, Bu, Wu, Pu, Fu, Iu.bind(null, n))) : (Ru(), zu(), Bu());
		}
		function Pu(e) {
			if (rw !== ZC) {
				var t = iw.onRecoverableError;
				t(e, Vu(null));
			}
		}
		function Fu(e) {
			Ey = by(), Ar(mw === null ? cw : Ty, Ey, Dy, hw === JC, GC), mw = gw = e;
		}
		function Iu(e) {
			if ((nb & e) !== 0) {
				var t = rb;
				nb &= ~e, rb = null, e & 4194048 && !($ & 4194048) && !(ow & 4194048) && (yr(256), jr(Uy, Sh(), t)), e & 62914560 && !($ & 62914560) && !(ow & 62914560) && (yr(4194304), jr(eb, Sh(), t)), e & 2080374784 && !($ & 2080374784) && !(ow & 2080374784) && (yr(268435456), jr(tb, Sh(), t));
			}
		}
		function Lu() {
			rw === ew && (rw = ZC, Fl(aw, iw), rw = tw);
		}
		function Ru() {
			if (rw === QC) {
				rw = ZC;
				var e = iw, t = aw, n = ow, r = !!(t.flags & 13878);
				if (t.subtreeFlags & 13878 || r) {
					r = R.T, R.T = null;
					var i = z.p;
					z.p = Vh;
					var a = Z;
					Z |= aC;
					try {
						HS = n, US = e, GS = KS = !1, Ri(), jl(t, e, n), US = HS = null, n = xT;
						var o = ar(e.containerInfo), s = n.focusedElem, c = n.selectionRange;
						if (o !== s && s && s.ownerDocument && ir(s.ownerDocument.documentElement, s)) {
							if (c !== null && or(s)) {
								var l = c.start, u = c.end;
								if (u === void 0 && (u = l), "selectionStart" in s) s.selectionStart = l, s.selectionEnd = Math.min(u, s.value.length);
								else {
									var d = s.ownerDocument || document, f = d && d.defaultView || window;
									if (f.getSelection) {
										var p = f.getSelection(), m = s.textContent.length, h = Math.min(c.start, m), g = c.end === void 0 ? h : Math.min(c.end, m);
										!p.extend && h > g && (o = g, g = h, h = o);
										var _ = rr(s, h), v = rr(s, g);
										if (_ && v && (p.rangeCount !== 1 || p.anchorNode !== _.node || p.anchorOffset !== _.offset || p.focusNode !== v.node || p.focusOffset !== v.offset)) {
											var y = d.createRange();
											y.setStart(_.node, _.offset), p.removeAllRanges(), h > g ? (p.addRange(y), p.extend(v.node, v.offset)) : (y.setEnd(v.node, v.offset), p.addRange(y));
										}
									}
								}
							}
							for (d = [], p = s; p = p.parentNode;) p.nodeType === 1 && d.push({
								element: p,
								left: p.scrollLeft,
								top: p.scrollTop
							});
							for (typeof s.focus == "function" && s.focus(), s = 0; s < d.length; s++) {
								var b = d[s];
								b.element.scrollLeft = b.left, b.element.scrollTop = b.top;
							}
						}
						gE = !!bT, xT = bT = null;
					} finally {
						Z = a, z.p = i, R.T = r;
					}
				}
				e.current = t, rw = $C;
			}
		}
		function zu() {
			if (rw === $C) {
				rw = ZC;
				var e = gw;
				if (e !== null) {
					Ty = by();
					var t = Ey, n = Ty;
					!Tv || n <= t || (rb ? rb.run(console.timeStamp.bind(console, e, t, n, H, V, "secondary-light")) : console.timeStamp(e, t, n, H, V, "secondary-light"));
				}
				e = iw, t = aw, n = ow;
				var r = !!(t.flags & 8772);
				if (t.subtreeFlags & 8772 || r) {
					r = R.T, R.T = null;
					var i = z.p;
					z.p = Vh;
					var a = Z;
					Z |= aC;
					try {
						HS = n, US = e, Ri(), bl(e, t.alternate, t), US = HS = null;
					} finally {
						Z = a, z.p = i, R.T = r;
					}
				}
				e = cw, t = mw, Ey = by(), Ar(t === null ? e : Ty, Ey, Dy, hw === JC, GC), rw = ew;
			}
		}
		function Bu() {
			if (rw === tw || rw === ew) {
				if (rw === tw) {
					var e = Ey;
					Ey = by();
					var t = Ey, n = hw === JC;
					!Tv || t <= e || (rb ? rb.run(console.timeStamp.bind(console, n ? "Interrupted View Transition" : "Starting Animation", e, t, H, V, n ? "error" : "secondary-light")) : console.timeStamp(n ? "Interrupted View Transition" : "Starting Animation", e, t, H, V, n ? " error" : "secondary-light")), hw !== JC && (hw = XC);
				}
				rw = ZC, e = dw, dw = null, xh(), t = iw;
				var r = aw;
				n = ow;
				var i = uw, a = (n & 335544064) === n ? 10262 : 10256;
				(a = r.actualDuration !== 0 || (r.subtreeFlags & a) !== 0 || (r.flags & a) !== 0) ? rw = nw : (rw = ZC, aw = iw = null, Hu(t, t.pendingLanes), Cw = 0, ww = null);
				var o = t.pendingLanes;
				if (o === 0 && (KC = null), a || ed(t), o = lt(n), r = r.stateNode, Mh && typeof Mh.onCommitFiberRoot == "function") try {
					var s = (r.current.flags & 128) == 128;
					switch (o) {
						case Vh:
							var c = wh;
							break;
						case Hh:
							c = Th;
							break;
						case Uh:
							c = Eh;
							break;
						case Wh:
							c = Oh;
							break;
						default: c = Eh;
					}
					Mh.onCommitFiberRoot(jh, r, c, s);
				} catch (e) {
					Nh || (Nh = !0, console.error("React instrumentation encountered an error: %o", e));
				}
				if (Ph && t.memoizedUpdaters.clear(), nu(), i !== null) {
					s = R.T, c = z.p, z.p = Vh, R.T = null;
					try {
						var l = t.onRecoverableError;
						for (r = 0; r < i.length; r++) {
							var u = i[r], d = Vu(u.stack);
							w(u.source, l, u.value, d);
						}
					} finally {
						R.T = s, z.p = c;
					}
				}
				if (l = fw, u = pw, pw = null, l !== null && (fw = null, u === null && (u = []), e !== null)) for (d = 0; d < l.length; d++) i = (0, l[d])(u), i !== void 0 && e.finished.finally(i);
				ow & 3 && Uu(), ad(t), o = t.pendingLanes, n & 261930 && o & 42 ? (sb = !0, t === yw ? vw++ : (vw = 0, yw = t)) : (vw = 0, yw = null), a || mu(n, Ey), od(0, !1);
			}
		}
		function Vu(e) {
			return e = { componentStack: e }, Object.defineProperty(e, "digest", { get: function() {
				console.error("You are accessing \"digest\" from the errorInfo object passed to onRecoverableError. This property is no longer provided as part of errorInfo but can be accessed as a property of the Error instance itself.");
			} }), e;
		}
		function Hu(e, t) {
			(e.pooledCacheLanes &= t) === 0 && (t = e.pooledCache, t != null && (e.pooledCache = null, Ai(t)));
		}
		function Uu() {
			return dw !== null && (dw.skipTransition(), Ew || (Ew = !0, console.warn("A flushSync update cancelled a View Transition because it was called while the View Transition was still preparing. To preserve the synchronous semantics, React had to skip the View Transition. If you can, try to avoid flushSync() in a scenario that's likely to interfere.")), dw = null, hw = JC), Ru(), zu(), Bu(), Wu();
		}
		function Wu() {
			if (rw !== nw) return !1;
			var e = iw, t = sw;
			sw = 0;
			var n = lt(ow), r = Uh === 0 || Uh > n ? Uh : n;
			n = R.T;
			var i = z.p;
			try {
				z.p = r, R.T = null;
				var a = lw;
				lw = null, r = iw;
				var o = ow;
				if (rw = ZC, aw = iw = null, ow = 0, (Z & (iC | aC)) !== rC) throw Error("Cannot flush passive effects while already rendering.");
				yr(o), bw = !0, xw = !1;
				var s = 0;
				if (Dy = null, s = Sh(), hw === XC) jr(Ey, s, rb);
				else {
					var c = Ey, l = s, u = hw === YC;
					!Tv || l <= c || (GC ? GC.run(console.timeStamp.bind(console, u ? "Waiting for Paint" : "Waiting", c, l, H, V, "secondary-light")) : console.timeStamp(u ? "Waiting for Paint" : "Waiting", c, l, H, V, "secondary-light"));
				}
				c = Z, Z |= aC;
				var d = r.current;
				Ri(), Ql(d);
				var f = r.current;
				d = cw, Ri(), Wl(r, f, o, a, d), ed(r), Z = c;
				var p = Sh();
				if (f = s, d = GC, Dy === null ? !Tv || p <= f || (d ? d.run(console.timeStamp.bind(console, "Remaining Effects", f, p, H, V, "secondary-dark")) : console.timeStamp("Remaining Effects", f, p, H, V, "secondary-dark")) : kr(f, p, Dy, !0, d), mu(o, p), od(0, !1), xw ? r === ww ? Cw++ : (Cw = 0, ww = r) : Cw = 0, xw = bw = !1, Mh && typeof Mh.onPostCommitFiberRoot == "function") try {
					Mh.onPostCommitFiberRoot(jh, r);
				} catch (e) {
					Nh || (Nh = !0, console.error("React instrumentation encountered an error: %o", e));
				}
				var m = r.current.stateNode;
				return m.effectDuration = 0, m.passiveEffectDuration = 0, !0;
			} finally {
				z.p = i, R.T = n, Hu(e, t);
			}
		}
		function Gu(e, t, n) {
			t = Qr(n, t), Yi(t), t = Ns(e.stateNode, t, 2), e = Ea(e, t, 2), e !== null && (tt(e, 2), ad(e));
		}
		function Ku(e, t, n) {
			if (Tw = !1, e.tag === 3) Gu(e, e, n);
			else {
				for (; t !== null;) {
					if (t.tag === 3) {
						Gu(t, e, n);
						return;
					}
					if (t.tag === 1) {
						var r = t.stateNode;
						if (typeof t.type.getDerivedStateFromError == "function" || typeof r.componentDidCatch == "function" && (KC === null || !KC.has(r))) {
							e = Qr(n, e), Yi(e), n = Ps(2), r = Ea(t, n, 2), r !== null && (Fs(n, r, t, e), tt(r, 2), ad(r));
							return;
						}
					}
					t = t.return;
				}
				console.error("Internal React error: Attempted to capture a commit phase error inside a detached tree. This indicates a bug in React. Potential causes include deleting the same fiber more than once, committing an already-finished tree, or an inconsistent return pointer.\n\nError message:\n\n%s", n);
			}
		}
		function qu(e, t, n) {
			var r = e.pingCache;
			if (r === null) {
				r = e.pingCache = new nC();
				var i = /* @__PURE__ */ new Set();
				r.set(t, i);
			} else i = r.get(t), i === void 0 && (i = /* @__PURE__ */ new Set(), r.set(t, i));
			i.has(n) || (OC = !0, i.add(n), r = Ju.bind(null, e, t, n), Ph && nd(e, n), t.then(r, r));
		}
		function Ju(e, t, n) {
			var r = e.pingCache;
			r !== null && r.delete(t), e.pingedLanes |= e.suspendedLanes & n, e.warmLanes &= ~n, n & 127 ? 0 > Py && (Ny = Py = by(), Fy = xy("Promise Resolved"), Iy = Cy) : n & 4194048 && 0 > Gy && (Uy = Gy = by(), qy = xy("Promise Resolved"), Ky = Cy), ru() && R.actQueue === null && console.error("A suspended resource finished loading inside a test, but the event was not wrapped in act(...).\n\nWhen testing, code that resolves suspended data should be wrapped into act(...):\n\nact(() => {\n  /* finish loading suspended data */\n});\n/* assert on the output */\n\nThis ensures that you're testing the behavior the user would see in the browser. Learn more at https://react.dev/link/wrap-tests-with-act"), pC === e && ($ & n) === n && ((AC === uC || AC === lC && ($ & 62914560) === $ && Sh() - zC < VC) && (Z & iC) === rC ? hu(e, 0) : NC |= n, FC === $ && (FC = 0)), ad(e);
		}
		function Yu(e, t) {
			t === 0 && (t = $e()), e = Fr(e, t), e !== null && (tt(e, t), ad(e));
		}
		function Xu(e) {
			var t = e.memoizedState, n = 0;
			t !== null && (n = t.retryLane), Yu(e, n);
		}
		function Zu(e, t) {
			var n = 0;
			switch (e.tag) {
				case 31:
				case 13:
					var r = e.stateNode, i = e.memoizedState;
					i !== null && (n = i.retryLane);
					break;
				case 19:
					r = e.stateNode;
					break;
				case 22:
					r = e.stateNode._retryCache;
					break;
				default: throw Error("Pinged unknown suspense boundary type. This is probably a bug in React.");
			}
			r !== null && r.delete(t), Yu(e, n);
		}
		function Qu(e, t, n) {
			if (t.subtreeFlags & 134225920) for (t = t.child; t !== null;) {
				var r = e, i = t, a = i.type === Mm;
				a = n || a, i.tag === 22 ? i.memoizedState === null && (a && i.flags & 134225920 ? w(i, $u, r, i) : i.subtreeFlags & 134217728 && w(i, Qu, r, i, a)) : i.flags & 134217728 ? a && w(i, $u, r, i) : Qu(r, i, a), t = t.sibling;
			}
		}
		function $u(e, t) {
			Ke(!0);
			try {
				Ll(t, NS), eu(t), zl(e, t.alternate, t, NS), Kl(e, t, 0, null, !1, 0);
			} finally {
				Ke(!1);
			}
		}
		function ed(e) {
			var t = !0;
			e.current.mode & (Hv | Uv) || (t = !1), Qu(e, e.current, t);
		}
		function td(e) {
			if ((Z & iC) === rC) {
				var t = e.tag;
				if (t === 3 || t === 1 || t === 0 || t === 11 || t === 14 || t === 15) {
					if (t = S(e) || "ReactComponent", Dw !== null) {
						if (Dw.has(t)) return;
						Dw.add(t);
					} else Dw = /* @__PURE__ */ new Set([t]);
					w(e, function() {
						console.error("Can't perform a React state update on a component that hasn't mounted yet. This indicates that you have a side-effect in your render function that asynchronously tries to update the component. Move this work to useEffect instead.");
					});
				}
			}
		}
		function nd(e, t) {
			Ph && e.memoizedUpdaters.forEach(function(n) {
				st(e, n, t);
			});
		}
		function rd(e, t) {
			var n = R.actQueue;
			return n === null ? vh(e, t) : (n.push(t), Aw);
		}
		function id(e) {
			ru() && R.actQueue === null && w(e, function() {
				console.error("An update to %s inside a test was not wrapped in act(...).\n\nWhen testing, code that causes React state updates should be wrapped into act(...):\n\nact(() => {\n  /* fire events that update state */\n});\n/* assert on the output */\n\nThis ensures that you're testing the behavior the user would see in the browser. Learn more at https://react.dev/link/wrap-tests-with-act", S(e));
			});
		}
		function ad(e) {
			e !== Mw && e.next === null && (Mw === null ? jw = Mw = e : Mw = Mw.next = e), Fw = !0, R.actQueue === null ? Nw || (Nw = !0, pd()) : Pw || (Pw = !0, pd());
		}
		function od(e, t) {
			if (!Iw && Fw) {
				Iw = !0;
				do
					for (var n = !1, r = jw; r !== null;) {
						if (!t) {
							if (e !== 0) {
								var i = r.pendingLanes;
								if (i === 0) var a = 0;
								else {
									var o = r.suspendedLanes, s = r.pingedLanes;
									a = (1 << 31 - Fh(42 | e) + 1) - 1, a &= i & ~(o & ~s), a = a & 201326741 ? a & 201326741 | 1 : a ? a | 2 : 0;
								}
								a !== 0 && (n = !0, dd(r, a));
							} else a = $, a = Ye(r, r === pC ? a : 0, r.cancelPendingCommit !== null || r.timeoutHandle !== OT), !(a & 3) || Xe(r, a) || (n = !0, dd(r, a));
						}
						r = r.next;
					}
				while (n);
				Iw = !1;
			}
		}
		function sd() {
			TT = window.event, cd();
		}
		function cd() {
			Fw = Pw = Nw = !1;
			var e = 0;
			Lw !== 0 && tf() && (e = Lw);
			for (var t = Sh(), n = null, r = jw; r !== null;) {
				var i = r.next, a = ld(r, t);
				a === 0 ? (r.next = null, n === null ? jw = i : n.next = i, i === null && (Mw = n)) : (n = r, (e !== 0 || a & 3) && (Fw = !0)), r = i;
			}
			rw !== ZC && rw !== nw || od(e, !1), Lw !== 0 && (Lw = 0);
		}
		function ld(e, t) {
			for (var n = e.suspendedLanes, r = e.pingedLanes, i = e.expirationTimes, a = e.pendingLanes & -62914561; 0 < a;) {
				var o = 31 - Fh(a), s = 1 << o, c = i[o];
				c === -1 ? ((s & n) === 0 || (s & r) !== 0) && (i[o] = Qe(s, t)) : c <= t && (e.expiredLanes |= s), a &= ~s;
			}
			if (t = pC, n = $, n = Ye(e, e === t ? n : 0, e.cancelPendingCommit !== null || e.timeoutHandle !== OT), r = e.callbackNode, n === 0 || e === t && (wC === gC || wC === CC) || e.cancelPendingCommit !== null) return r !== null && fd(r), e.callbackNode = null, e.callbackPriority = 0;
			if (!(n & 3) || Xe(e, n)) {
				if (t = n & -n, t !== e.callbackPriority || R.actQueue !== null && r !== Rw) fd(r);
				else return t;
				switch (lt(n)) {
					case Vh:
					case Hh:
						n = Th;
						break;
					case Uh:
						n = Eh;
						break;
					case Wh:
						n = Oh;
						break;
					default: n = Eh;
				}
				return r = ud.bind(null, e), R.actQueue === null ? n = vh(n, r) : (R.actQueue.push(r), n = Rw), e.callbackPriority = t, e.callbackNode = n, t;
			}
			return r !== null && fd(r), e.callbackPriority = 2, e.callbackNode = null, 2;
		}
		function ud(e, t) {
			if (sb = ob = !1, TT = window.event, rw !== ZC && rw !== nw) return e.callbackNode = null, e.callbackPriority = 0, null;
			var n = e.callbackNode;
			if (hw === qC && (hw = YC), Uu() && e.callbackNode !== n) return null;
			var r = $;
			return r = Ye(e, e === pC ? r : 0, e.cancelPendingCommit !== null || e.timeoutHandle !== OT), r === 0 ? null : (cu(e, r, t), ld(e, Sh()), e.callbackNode != null && e.callbackNode === n ? ud.bind(null, e) : null);
		}
		function dd(e, t) {
			if (Uu()) return null;
			ob = sb, sb = !1, cu(e, t, !0);
		}
		function fd(e) {
			e !== Rw && e !== null && yh(e);
		}
		function pd() {
			R.actQueue !== null && R.actQueue.push(function() {
				return cd(), null;
			}), jT(function() {
				(Z & (iC | aC)) === rC ? cd() : vh(wh, sd);
			});
		}
		function md() {
			if (Lw === 0) {
				var e = ub;
				e === 0 && (e = Rh, Rh <<= 1, !(Rh & 261888) && (Rh = 256)), Lw = e;
			}
			return Lw;
		}
		function hd(e) {
			return e == null || typeof e == "symbol" || typeof e == "boolean" ? null : typeof e == "function" ? e : (He(e, "action"), wn(e));
		}
		function M(e, t, n, r, i) {
			if (t === "submit" && n && n.stateNode === i) {
				var a = hd((i[qh] || null).action), o = r.submitter;
				o && (t = (t = o[qh] || null) ? hd(t.formAction) : o.getAttribute("formAction"), t !== null && (a = t, o = null));
				var s = new c_("action", "action", null, r, i);
				e.push({
					event: s,
					listeners: [{
						instance: null,
						listener: function() {
							if (r.defaultPrevented) {
								if (Lw !== 0) {
									var e = new FormData(i, o), t = {
										pending: !0,
										data: e,
										method: i.method,
										action: a
									};
									Object.freeze(t), os(n, t, null, e);
								}
							} else typeof a == "function" && (s.preventDefault(), e = new FormData(i, o), t = {
								pending: !0,
								data: e,
								method: i.method,
								action: a
							}, Object.freeze(t), os(n, t, a, e));
						},
						currentTarget: i
					}]
				});
			}
		}
		function N(e, t, n) {
			e.currentTarget = n;
			try {
				t(e);
			} catch (e) {
				hv(e);
			}
			e.currentTarget = null;
		}
		function gd(e, t) {
			t = !!(t & 4);
			for (var n = 0; n < e.length; n++) {
				var r = e[n];
				a: {
					var i = void 0, a = r.event;
					if (r = r.listeners, t) for (var o = r.length - 1; 0 <= o; o--) {
						var s = r[o], c = s.instance, l = s.currentTarget;
						if (s = s.listener, c !== i && a.isPropagationStopped()) break a;
						c === null ? N(a, s, l) : w(c, N, a, s, l), i = c;
					}
					else for (o = 0; o < r.length; o++) {
						if (s = r[o], c = s.instance, l = s.currentTarget, s = s.listener, c !== i && a.isPropagationStopped()) break a;
						c === null ? N(a, s, l) : w(c, N, a, s, l), i = c;
					}
				}
			}
		}
		function P(e, t) {
			Bw.has(e) || console.error("Did not expect a listenToNonDelegatedEvent() call for \"%s\". This is a bug in React. Please file an issue.", e);
			var n = t[Yh];
			n === void 0 && (n = t[Yh] = /* @__PURE__ */ new Set());
			var r = e + "__bubble";
			n.has(r) || (yd(t, e, 2, !1), n.add(r));
		}
		function _d(e, t, n) {
			Bw.has(e) && !t && console.error("Did not expect a listenToNativeEvent() call for \"%s\" in the bubble phase. This is a bug in React. Please file an issue.", e);
			var r = 0;
			t && (r |= 4), yd(n, e, r, t);
		}
		function vd(e) {
			if (!e[Vw]) {
				e[Vw] = !0, tg.forEach(function(t) {
					t !== "selectionchange" && (Bw.has(t) || _d(t, !1, e), _d(t, !0, e));
				});
				var t = e.nodeType === 9 ? e : e.ownerDocument;
				t === null || t[Vw] || (t[Vw] = !0, _d("selectionchange", !1, t));
			}
		}
		function yd(e, t, n, r) {
			switch (cm(t)) {
				case Vh:
					var i = rm;
					break;
				case Hh:
					i = im;
					break;
				default: i = am;
			}
			n = i.bind(null, t, n, e), i = void 0, !n_ || t !== "touchstart" && t !== "touchmove" && t !== "wheel" || (i = !0), r ? i === void 0 ? e.addEventListener(t, n, !0) : e.addEventListener(t, n, {
				capture: !0,
				passive: i
			}) : i === void 0 ? e.addEventListener(t, n, !1) : e.addEventListener(t, n, { passive: i });
		}
		function bd(e, t, n, r, i) {
			var a = r;
			if (!(t & 1) && !(t & 2) && r !== null) a: for (;;) {
				if (r === null) return;
				var o = r.tag;
				if (o === 3 || o === 4) {
					var s = r.stateNode.containerInfo;
					if (s === i) break;
					if (o === 4) for (o = r.return; o !== null;) {
						var c = o.tag;
						if ((c === 3 || c === 4) && o.stateNode.containerInfo === i) return;
						o = o.return;
					}
					for (; s !== null;) {
						if (o = pt(s), o === null) return;
						if (c = o.tag, c === 5 || c === 6 || c === 26 || c === 27) {
							r = a = o;
							continue a;
						}
						s = s.parentNode;
					}
				}
				r = r.return;
			}
			On(function() {
				var r = a, i = En(n), o = [];
				a: {
					var s = cv.get(e);
					if (s !== void 0) {
						var c = c_, l = e;
						switch (e) {
							case "keypress": if (jn(n) === 0) break a;
							case "keydown":
							case "keyup":
								c = T_;
								break;
							case "focusin":
								l = "focus", c = __;
								break;
							case "focusout":
								l = "blur", c = __;
								break;
							case "beforeblur":
							case "afterblur":
								c = __;
								break;
							case "click": if (n.button === 2) break a;
							case "auxclick":
							case "dblclick":
							case "mousedown":
							case "mousemove":
							case "mouseup":
							case "mouseout":
							case "mouseover":
							case "contextmenu":
								c = h_;
								break;
							case "drag":
							case "dragend":
							case "dragenter":
							case "dragexit":
							case "dragleave":
							case "dragover":
							case "dragstart":
							case "drop":
								c = g_;
								break;
							case "touchcancel":
							case "touchend":
							case "touchmove":
							case "touchstart":
								c = O_;
								break;
							case tv:
							case nv:
							case rv:
								c = v_;
								break;
							case sv:
								c = k_;
								break;
							case "scroll":
							case "scrollend":
								c = u_;
								break;
							case "wheel":
								c = A_;
								break;
							case "copy":
							case "cut":
							case "paste":
								c = y_;
								break;
							case "gotpointercapture":
							case "lostpointercapture":
							case "pointercancel":
							case "pointerdown":
							case "pointermove":
							case "pointerout":
							case "pointerover":
							case "pointerup":
								c = E_;
								break;
							case "submit":
								c = D_;
								break;
							case "toggle":
							case "beforetoggle": c = j_;
						}
						var u = !!(t & 4), d = !u && (e === "scroll" || e === "scrollend"), f = u ? s === null ? null : s + "Capture" : s;
						u = [];
						for (var p = r, m; p !== null;) {
							var h = p;
							if (m = h.stateNode, h = h.tag, h !== 5 && h !== 26 && h !== 27 || m === null || f === null || (h = kn(p, f), h != null && u.push(xd(p, h, m))), d) break;
							p = p.return;
						}
						0 < u.length && (s = new c(s, l, null, n, i), o.push({
							event: s,
							listeners: u
						}));
					}
				}
				if (!(t & 7)) {
					a: {
						if (c = e === "mouseover" || e === "pointerover", s = e === "mouseout" || e === "pointerout", c && n !== Zg && (l = n.relatedTarget || n.fromElement) && (pt(l) || l[Jh])) break a;
						(s || c) && (l = i.window === i ? i : (c = i.ownerDocument) ? c.defaultView || c.parentWindow : window, s ? (c = n.relatedTarget || n.toElement, s = r, c = c ? pt(c) : null, c !== null && (d = ee(c), u = c.tag, c !== d || u !== 5 && u !== 27 && u !== 6) && (c = null)) : (s = null, c = r), s !== c && (u = h_, h = "onMouseLeave", f = "onMouseEnter", p = "mouse", (e === "pointerout" || e === "pointerover") && (u = E_, h = "onPointerLeave", f = "onPointerEnter", p = "pointer"), d = s == null ? l : ht(s), m = c == null ? l : ht(c), l = new u(h, p + "leave", s, n, i), l.target = d, l.relatedTarget = m, h = null, pt(i) === r && (u = new u(f, p + "enter", c, n, i), u.target = m, u.relatedTarget = d, h = u), d = h, u = s && c ? he(s, c, Cd) : null, s !== null && wd(o, l, s, u, !1), c !== null && d !== null && wd(o, d, c, u, !0)));
					}
					a: {
						if (s = r ? ht(r) : window, c = s.nodeName && s.nodeName.toLowerCase(), c === "select" || c === "input" && s.type === "file") var g = Kn;
						else if (Vn(s)) {
							if (G_) g = Qn;
							else {
								g = Xn;
								var _ = Yn;
							}
						} else c = s.nodeName, !c || c.toLowerCase() !== "input" || s.type !== "checkbox" && s.type !== "radio" ? r && vn(r.elementType) && (g = Kn) : g = Zn;
						if (g &&= g(e, r)) {
							Un(o, g, n, i);
							break a;
						}
						_ && _(e, s, r);
					}
					switch (_ = r ? ht(r) : window, e) {
						case "focusin":
							(Vn(_) || _.contentEditable === "true") && (J_ = _, Y_ = r, X_ = null);
							break;
						case "focusout":
							X_ = Y_ = J_ = null;
							break;
						case "mousedown":
							Z_ = !0;
							break;
						case "contextmenu":
						case "mouseup":
						case "dragend":
							Z_ = !1, sr(o, n, i);
							break;
						case "selectionchange": if (q_) break;
						case "keydown":
						case "keyup": sr(o, n, i);
					}
					var v;
					if (P_) b: {
						switch (e) {
							case "compositionstart":
								var y = "onCompositionStart";
								break b;
							case "compositionend":
								y = "onCompositionEnd";
								break b;
							case "compositionupdate":
								y = "onCompositionUpdate";
								break b;
						}
						y = void 0;
					}
					else V_ ? Ln(e, n) && (y = "onCompositionEnd") : e === "keydown" && n.keyCode === N_ && (y = "onCompositionStart");
					y && (L_ && n.locale !== "ko" && (V_ || y !== "onCompositionStart" ? y === "onCompositionEnd" && V_ && (v = An()) : (i_ = i, a_ = "value" in i_ ? i_.value : i_.textContent, V_ = !0)), _ = Sd(r, y), 0 < _.length && (y = new b_(y, e, null, n, i), o.push({
						event: y,
						listeners: _
					}), v ? y.data = v : (v = Rn(n), v !== null && (y.data = v)))), (v = I_ ? zn(e, n) : Bn(e, n)) && (y = Sd(r, "onBeforeInput"), 0 < y.length && (_ = new x_("onBeforeInput", "beforeinput", null, n, i), o.push({
						event: _,
						listeners: y
					}), _.data = v)), M(o, e, r, n, i);
				}
				gd(o, t);
			});
		}
		function xd(e, t, n) {
			return {
				instance: e,
				listener: t,
				currentTarget: n
			};
		}
		function Sd(e, t) {
			for (var n = t + "Capture", r = []; e !== null;) {
				var i = e, a = i.stateNode;
				if (i = i.tag, i !== 5 && i !== 26 && i !== 27 || a === null || (i = kn(e, n), i != null && r.unshift(xd(e, i, a)), i = kn(e, t), i != null && r.push(xd(e, i, a))), e.tag === 3) return r;
				e = e.return;
			}
			return [];
		}
		function Cd(e) {
			if (e === null) return null;
			do
				e = e.return;
			while (e && e.tag !== 5 && e.tag !== 27);
			return e || null;
		}
		function wd(e, t, n, r, i) {
			for (var a = t._reactName, o = []; n !== null && n !== r;) {
				var s = n, c = s.alternate, l = s.stateNode;
				if (s = s.tag, c !== null && c === r) break;
				s !== 5 && s !== 26 && s !== 27 || l === null || (c = l, i ? (l = kn(n, a), l != null && o.unshift(xd(n, l, c))) : i || (l = kn(n, a), l != null && o.push(xd(n, l, c)))), n = n.return;
			}
			o.length !== 0 && e.push({
				event: t,
				listeners: o
			});
		}
		function Td(e, t) {
			xn(e, t), e !== "input" && e !== "textarea" && e !== "select" || t == null || t.value !== null || Wg || (Wg = !0, e === "select" && t.multiple ? console.error("`value` prop on `%s` should not be null. Consider using an empty array when `multiple` is set to `true` to clear the component or `undefined` for uncontrolled components.", e) : console.error("`value` prop on `%s` should not be null. Consider using an empty string to clear the component or `undefined` for uncontrolled components.", e));
			var n = {
				registrationNameDependencies: ng,
				possibleRegistrationNames: rg
			};
			vn(e) || typeof t.is == "string" || Cn(e, t, n), t.contentEditable && !t.suppressContentEditableWarning && t.children != null && console.error("A component is `contentEditable` and contains `children` managed by React. It is now your responsibility to guarantee that none of those nodes are unexpectedly modified or duplicated. This is probably not intentional.");
		}
		function Ed(e, t, n, r) {
			t !== n && (n = Md(n), Md(t) !== n && (r[e] = t));
		}
		function Dd(e) {
			return !!(e.getAttribute("vt-share") || e.getAttribute("vt-exit") || e.getAttribute("vt-enter") || e.getAttribute("vt-update"));
		}
		function Od(e) {
			if (!Dd(e)) return !1;
			var t = e.getAttribute("vt-name");
			return e = e.style["view-transition-name"], t ? t === e : e.startsWith("_T_");
		}
		function kd(e, t, n) {
			t.forEach(function(t) {
				t === "style" ? e.getAttribute(t) !== "" && (t = e.style, (t.length === 1 && t[0] === "view-transition-name" || t.length === 2 && t[0] === "view-transition-class" && t[1] === "view-transition-name") && Od(e) || (n.style = Rd(e))) : n[Ld(t)] = e.getAttribute(t);
			});
		}
		function Ad(e, t) {
			!1 === t ? console.error("Expected `%s` listener to be a function, instead got `false`.\n\nIf you used to conditionally omit it with %s={condition && value}, pass %s={condition ? value : undefined} instead.", e, e, e) : console.error("Expected `%s` listener to be a function, instead got a value of `%s` type.", e, typeof t);
		}
		function jd(e, t) {
			return e = e.namespaceURI === Ig || e.namespaceURI === Lg ? e.ownerDocument.createElementNS(e.namespaceURI, e.tagName) : e.ownerDocument.createElement(e.tagName), e.innerHTML = t, e.innerHTML;
		}
		function Md(e) {
			return Be(e) && (console.error("The provided HTML markup uses a value of unsupported type %s. This value must be coerced to a string before using it here.", ze(e)), Ve(e)), (typeof e == "string" ? e : "" + e).replace(Xw, "\n").replace(Zw, "");
		}
		function Nd(e, t) {
			return t = Md(t), Md(e) === t;
		}
		function F(e, t, n, r, i, a) {
			switch (n) {
				case "children":
					if (typeof r == "string") pn(r, t, !1), t === "body" || t === "textarea" && r === "" || mn(e, r);
					else if (typeof r == "number" || typeof r == "bigint") pn("" + r, t, !1), t !== "body" && mn(e, "" + r);
					else return;
					break;
				case "className":
					Et(e, "class", r);
					break;
				case "tabIndex":
					Et(e, "tabindex", r);
					break;
				case "dir":
				case "role":
				case "viewBox":
				case "width":
				case "height":
					Et(e, n, r);
					break;
				case "style":
					_n(e, r, a);
					return;
				case "data": if (t !== "object") {
					Et(e, "data", r);
					break;
				}
				case "src":
				case "href":
					if (r === "" && (t !== "a" || n !== "href")) {
						console.error(n === "src" ? "An empty string (\"\") was passed to the %s attribute. This may cause the browser to download the whole page again over the network. To fix this, either do not render the element at all or pass null to %s instead of an empty string." : "An empty string (\"\") was passed to the %s attribute. To fix this, either do not render the element at all or pass null to %s instead of an empty string.", n, n), e.removeAttribute(n);
						break;
					}
					if (r == null || typeof r == "function" || typeof r == "symbol" || typeof r == "boolean") {
						e.removeAttribute(n);
						break;
					}
					He(r, n), r = wn(r), e.setAttribute(n, r);
					break;
				case "action":
				case "formAction":
					if (r != null && (t === "form" ? n === "formAction" ? console.error("You can only pass the formAction prop to <input> or <button>. Use the action prop on <form>.") : typeof r == "function" && (i.encType == null && i.method == null || qw || (qw = !0, console.error("Cannot specify a encType or method for a form that specifies a function as the action. React provides those automatically. They will get overridden.")), i.target == null || Kw || (Kw = !0, console.error("Cannot specify a target for a form that specifies a function as the action. The function will always be executed in the same window."))) : t === "input" || t === "button" ? n === "action" ? console.error("You can only pass the action prop to <form>. Use the formAction prop on <input> or <button>.") : t !== "input" || i.type === "submit" || i.type === "image" || Ww ? t !== "button" || i.type == null || i.type === "submit" || Ww ? typeof r == "function" && (i.name == null || Gw || (Gw = !0, console.error("Cannot specify a \"name\" prop for a button that specifies a function as a formAction. React needs it to encode which action should be invoked. It will get overridden.")), i.formEncType == null && i.formMethod == null || qw || (qw = !0, console.error("Cannot specify a formEncType or formMethod for a button that specifies a function as a formAction. React provides those automatically. They will get overridden.")), i.formTarget == null || Kw || (Kw = !0, console.error("Cannot specify a formTarget for a button that specifies a function as a formAction. The function will always be executed in the same window."))) : (Ww = !0, console.error("A button can only specify a formAction along with type=\"submit\" or no type.")) : (Ww = !0, console.error("An input can only specify a formAction along with type=\"submit\" or type=\"image\".")) : console.error(n === "action" ? "You can only pass the action prop to <form>." : "You can only pass the formAction prop to <input> or <button>.")), typeof r == "function") {
						e.setAttribute(n, "javascript:throw new Error('A React form was unexpectedly submitted. If you called form.submit() manually, consider using form.requestSubmit() instead. If you\\'re trying to use event.stopPropagation() in a submit event handler, consider also calling event.preventDefault().')");
						break;
					}
					if (typeof a == "function" && (n === "formAction" ? (t !== "input" && F(e, t, "name", i.name, i, null), F(e, t, "formEncType", i.formEncType, i, null), F(e, t, "formMethod", i.formMethod, i, null), F(e, t, "formTarget", i.formTarget, i, null)) : (F(e, t, "encType", i.encType, i, null), F(e, t, "method", i.method, i, null), F(e, t, "target", i.target, i, null))), r == null || typeof r == "symbol" || typeof r == "boolean") {
						e.removeAttribute(n);
						break;
					}
					He(r, n), r = wn(r), e.setAttribute(n, r);
					break;
				case "onClick":
					r != null && (typeof r != "function" && Ad(n, r), e.onclick = Tn);
					return;
				case "onScroll":
					r != null && (typeof r != "function" && Ad(n, r), P("scroll", e));
					return;
				case "onScrollEnd":
					r != null && (typeof r != "function" && Ad(n, r), P("scrollend", e));
					return;
				case "dangerouslySetInnerHTML":
					if (r != null) {
						if (typeof r != "object" || !("__html" in r)) throw Error("`props.dangerouslySetInnerHTML` must be in the form `{__html: ...}`. Please visit https://react.dev/link/dangerously-set-inner-html for more information.");
						if (n = r.__html, n != null) {
							if (i.children != null) throw Error("Can only set one of `children` or `props.dangerouslySetInnerHTML`.");
							a?.__html !== n && (e.innerHTML = n);
						}
					}
					break;
				case "multiple":
					e.multiple = r && typeof r != "function" && typeof r != "symbol";
					break;
				case "muted":
					e.muted = r && typeof r != "function" && typeof r != "symbol";
					break;
				case "suppressContentEditableWarning":
				case "suppressHydrationWarning":
				case "defaultValue":
				case "defaultChecked":
				case "innerHTML":
				case "ref": break;
				case "autoFocus": break;
				case "xlinkHref":
					if (r == null || typeof r == "function" || typeof r == "boolean" || typeof r == "symbol") {
						e.removeAttribute("xlink:href");
						break;
					}
					He(r, n), n = wn(r), e.setAttributeNS(Qw, "xlink:href", n);
					break;
				case "contentEditable":
				case "spellCheck":
				case "draggable":
				case "value":
				case "autoReverse":
				case "externalResourcesRequired":
				case "focusable":
				case "preserveAlpha":
					r != null && typeof r != "function" && typeof r != "symbol" ? (He(r, n), e.setAttribute(n, r)) : e.removeAttribute(n);
					break;
				case "inert": r !== "" || Yw[n] || (Yw[n] = !0, console.error("Received an empty string for a boolean attribute `%s`. This will treat the attribute as if it were false. Either pass `false` to silence this warning, or pass `true` if you used an empty string in earlier versions of React to indicate this attribute is true.", n));
				case "allowFullScreen":
				case "async":
				case "autoPlay":
				case "controls":
				case "credentialless":
				case "default":
				case "defer":
				case "disabled":
				case "disablePictureInPicture":
				case "disableRemotePlayback":
				case "formNoValidate":
				case "hidden":
				case "loop":
				case "noModule":
				case "noValidate":
				case "open":
				case "playsInline":
				case "readOnly":
				case "required":
				case "reversed":
				case "scoped":
				case "seamless":
				case "itemScope":
					r && typeof r != "function" && typeof r != "symbol" ? e.setAttribute(n, "") : e.removeAttribute(n);
					break;
				case "capture":
				case "download":
					!0 === r ? e.setAttribute(n, "") : !1 !== r && r != null && typeof r != "function" && typeof r != "symbol" ? (He(r, n), e.setAttribute(n, r)) : e.removeAttribute(n);
					break;
				case "cols":
				case "rows":
				case "size":
				case "span":
					r != null && typeof r != "function" && typeof r != "symbol" && !isNaN(r) && 1 <= r ? (He(r, n), e.setAttribute(n, r)) : e.removeAttribute(n);
					break;
				case "rowSpan":
				case "start":
					r == null || typeof r == "function" || typeof r == "symbol" || isNaN(r) ? e.removeAttribute(n) : (He(r, n), e.setAttribute(n, r));
					break;
				case "popover":
					P("beforetoggle", e), P("toggle", e), Tt(e, "popover", r);
					break;
				case "xlinkActuate":
					Dt(e, Qw, "xlink:actuate", r);
					break;
				case "xlinkArcrole":
					Dt(e, Qw, "xlink:arcrole", r);
					break;
				case "xlinkRole":
					Dt(e, Qw, "xlink:role", r);
					break;
				case "xlinkShow":
					Dt(e, Qw, "xlink:show", r);
					break;
				case "xlinkTitle":
					Dt(e, Qw, "xlink:title", r);
					break;
				case "xlinkType":
					Dt(e, Qw, "xlink:type", r);
					break;
				case "xmlBase":
					Dt(e, $w, "xml:base", r);
					break;
				case "xmlLang":
					Dt(e, $w, "xml:lang", r);
					break;
				case "xmlSpace":
					Dt(e, $w, "xml:space", r);
					break;
				case "is":
					a != null && console.error("Cannot update the \"is\" prop after it has been initialized."), Tt(e, "is", r);
					break;
				case "innerText":
				case "textContent": return;
				case "popoverTarget": Jw || typeof r != "object" || !r || (Jw = !0, console.error("The `popoverTarget` prop expects the ID of an Element as a string. Received %s instead.", r));
				default: if (!(2 < n.length) || n[0] !== "o" && n[0] !== "O" || n[1] !== "n" && n[1] !== "N") n = yn(n), Tt(e, n, r);
				else {
					ng.hasOwnProperty(n) && r != null && typeof r != "function" && Ad(n, r);
					return;
				}
			}
			B = !0;
		}
		function Pd(e, t, n, r, i, a) {
			switch (n) {
				case "style":
					_n(e, r, a);
					return;
				case "dangerouslySetInnerHTML":
					if (r != null) {
						if (typeof r != "object" || !("__html" in r)) throw Error("`props.dangerouslySetInnerHTML` must be in the form `{__html: ...}`. Please visit https://react.dev/link/dangerously-set-inner-html for more information.");
						if (n = r.__html, n != null) {
							if (i.children != null) throw Error("Can only set one of `children` or `props.dangerouslySetInnerHTML`.");
							a?.__html !== n && (e.innerHTML = n);
						}
					}
					break;
				case "children":
					if (typeof r == "string") mn(e, r);
					else if (typeof r == "number" || typeof r == "bigint") mn(e, "" + r);
					else return;
					break;
				case "onScroll":
					r != null && (typeof r != "function" && Ad(n, r), P("scroll", e));
					return;
				case "onScrollEnd":
					r != null && (typeof r != "function" && Ad(n, r), P("scrollend", e));
					return;
				case "onClick":
					r != null && (typeof r != "function" && Ad(n, r), e.onclick = Tn);
					return;
				case "suppressContentEditableWarning":
				case "suppressHydrationWarning":
				case "innerHTML":
				case "ref": return;
				case "innerText":
				case "textContent": return;
				default:
					if (ng.hasOwnProperty(n)) r != null && typeof r != "function" && Ad(n, r);
					else a: {
						if (n[0] === "o" && n[1] === "n" && (i = n.endsWith("Capture"), a = n.slice(2, i ? n.length - 7 : void 0), t = e[qh] || null, t = t == null ? null : t[n], typeof t == "function" && e.removeEventListener(a, t, i), typeof r == "function")) {
							typeof t != "function" && t !== null && (n in e ? e[n] = null : e.hasAttribute(n) && e.removeAttribute(n)), e.addEventListener(a, r, i);
							break a;
						}
						B = !0, n in e ? e[n] = r : !0 === r ? e.setAttribute(n, "") : Tt(e, n, r);
					}
					return;
			}
			B = !0;
		}
		function Fd(e, t, n) {
			switch (Td(t, n), t) {
				case "div":
				case "span":
				case "svg":
				case "path":
				case "a":
				case "g":
				case "p":
				case "li": break;
				case "img":
					P("error", e), P("load", e);
					var r = !1, i = !1, a;
					for (a in n) if (n.hasOwnProperty(a)) {
						var o = n[a];
						if (o != null) switch (a) {
							case "src":
								r = !0;
								break;
							case "srcSet":
								i = !0;
								break;
							case "children":
							case "dangerouslySetInnerHTML": throw Error(t + " is a void element tag and must neither have `children` nor use `dangerouslySetInnerHTML`.");
							default: F(e, t, a, o, n, null);
						}
					}
					i && F(e, t, "srcSet", n.srcSet, n, null), r && F(e, t, "src", n.src, n, null);
					return;
				case "input":
					xt("input", n), P("invalid", e);
					var s = a = o = i = null, c = null, l = null;
					for (r in n) if (n.hasOwnProperty(r)) {
						var u = n[r];
						if (u != null) switch (r) {
							case "name":
								i = u;
								break;
							case "type":
								o = u;
								break;
							case "checked":
								c = u;
								break;
							case "defaultChecked":
								l = u;
								break;
							case "value":
								a = u;
								break;
							case "defaultValue":
								s = u;
								break;
							case "children":
							case "dangerouslySetInnerHTML":
								if (u != null) throw Error(t + " is a void element tag and must neither have `children` nor use `dangerouslySetInnerHTML`.");
								break;
							default: F(e, t, r, u, n, null);
						}
					}
					Pt(e, n), It(e, a, s, c, l, o, i, !1);
					return;
				case "select":
					for (i in xt("select", n), P("invalid", e), r = o = a = null, n) if (n.hasOwnProperty(i) && (s = n[i], s != null)) switch (i) {
						case "value":
							a = s;
							break;
						case "defaultValue":
							o = s;
							break;
						case "multiple": r = s;
						default: F(e, t, i, s, n, null);
					}
					Vt(e, n), t = a, n = o, e.multiple = !!r, t == null ? n != null && Bt(e, !!r, n, !0) : Bt(e, !!r, t, !1);
					return;
				case "textarea":
					for (o in xt("textarea", n), P("invalid", e), a = i = r = null, n) if (n.hasOwnProperty(o) && (s = n[o], s != null)) switch (o) {
						case "value":
							r = s;
							break;
						case "defaultValue":
							i = s;
							break;
						case "children":
							a = s;
							break;
						case "dangerouslySetInnerHTML":
							if (s != null) throw Error("`dangerouslySetInnerHTML` does not make sense on <textarea>.");
							break;
						default: F(e, t, o, s, n, null);
					}
					Ht(e, n), Wt(e, r, i, a);
					return;
				case "option":
					for (c in Rt(e, n), n) if (n.hasOwnProperty(c) && (r = n[c], r != null)) switch (c) {
						case "selected":
							e.selected = r && typeof r != "function" && typeof r != "symbol";
							break;
						default: F(e, t, c, r, n, null);
					}
					return;
				case "dialog":
					P("beforetoggle", e), P("toggle", e), P("cancel", e), P("close", e);
					break;
				case "iframe":
				case "object":
					P("load", e);
					break;
				case "video":
				case "audio":
					for (r = 0; r < zw.length; r++) P(zw[r], e);
					break;
				case "image":
					P("error", e), P("load", e);
					break;
				case "details":
					P("toggle", e);
					break;
				case "embed":
				case "source":
				case "link": P("error", e), P("load", e);
				case "area":
				case "base":
				case "br":
				case "col":
				case "hr":
				case "keygen":
				case "meta":
				case "param":
				case "track":
				case "wbr":
				case "menuitem":
					for (l in n) if (n.hasOwnProperty(l) && (r = n[l], r != null)) switch (l) {
						case "children":
						case "dangerouslySetInnerHTML": throw Error(t + " is a void element tag and must neither have `children` nor use `dangerouslySetInnerHTML`.");
						default: F(e, t, l, r, n, null);
					}
					return;
				default: if (vn(t)) {
					for (u in n) n.hasOwnProperty(u) && (r = n[u], r !== void 0 && Pd(e, t, u, r, n, void 0));
					return;
				}
			}
			for (s in n) n.hasOwnProperty(s) && (r = n[s], r != null && F(e, t, s, r, n, null));
		}
		function Id(e, t, n, r) {
			switch (Td(t, r), t) {
				case "div":
				case "span":
				case "svg":
				case "path":
				case "a":
				case "g":
				case "p":
				case "li": break;
				case "input":
					var i = null, a = null, o = null, s = null, c = null, l = null, u = null;
					for (p in n) {
						var d = n[p];
						if (n.hasOwnProperty(p) && d != null) switch (p) {
							case "checked": break;
							case "value": break;
							case "defaultValue": c = d;
							default: r.hasOwnProperty(p) || F(e, t, p, null, r, d);
						}
					}
					for (var f in r) {
						var p = r[f];
						if (d = n[f], r.hasOwnProperty(f) && (p != null || d != null)) switch (f) {
							case "type":
								p !== d && (B = !0), a = p;
								break;
							case "name":
								p !== d && (B = !0), i = p;
								break;
							case "checked":
								p !== d && (B = !0), l = p;
								break;
							case "defaultChecked":
								p !== d && (B = !0), u = p;
								break;
							case "value":
								p !== d && (B = !0), o = p;
								break;
							case "defaultValue":
								p !== d && (B = !0), s = p;
								break;
							case "children":
							case "dangerouslySetInnerHTML":
								if (p != null) throw Error(t + " is a void element tag and must neither have `children` nor use `dangerouslySetInnerHTML`.");
								break;
							default: p !== d && F(e, t, f, p, r, d);
						}
					}
					t = n.type === "checkbox" || n.type === "radio" ? n.checked != null : n.value != null, r = r.type === "checkbox" || r.type === "radio" ? r.checked != null : r.value != null, t || !r || Uw || (console.error("A component is changing an uncontrolled input to be controlled. This is likely caused by the value changing from undefined to a defined value, which should not happen. Decide between using a controlled or uncontrolled input element for the lifetime of the component. More info: https://react.dev/link/controlled-components"), Uw = !0), !t || r || Hw || (console.error("A component is changing a controlled input to be uncontrolled. This is likely caused by the value changing from a defined to undefined, which should not happen. Decide between using a controlled or uncontrolled input element for the lifetime of the component. More info: https://react.dev/link/controlled-components"), Hw = !0), Ft(e, o, s, c, l, u, a, i);
					return;
				case "select":
					for (a in p = o = s = f = null, n) if (c = n[a], n.hasOwnProperty(a) && c != null) switch (a) {
						case "value": break;
						case "multiple": p = c;
						default: r.hasOwnProperty(a) || F(e, t, a, null, r, c);
					}
					for (i in r) if (a = r[i], c = n[i], r.hasOwnProperty(i) && (a != null || c != null)) switch (i) {
						case "value":
							a !== c && (B = !0), f = a;
							break;
						case "defaultValue":
							a !== c && (B = !0), s = a;
							break;
						case "multiple": a !== c && (B = !0), o = a;
						default: a !== c && F(e, t, i, a, r, c);
					}
					r = s, t = o, n = p, f == null ? !!n != !!t && (r == null ? Bt(e, !!t, t ? [] : "", !1) : Bt(e, !!t, r, !0)) : Bt(e, !!t, f, !1);
					return;
				case "textarea":
					for (s in p = f = null, n) if (i = n[s], n.hasOwnProperty(s) && i != null && !r.hasOwnProperty(s)) switch (s) {
						case "value": break;
						case "children": break;
						default: F(e, t, s, null, r, i);
					}
					for (o in r) if (i = r[o], a = n[o], r.hasOwnProperty(o) && (i != null || a != null)) switch (o) {
						case "value":
							i !== a && (B = !0), f = i;
							break;
						case "defaultValue":
							i !== a && (B = !0), p = i;
							break;
						case "children": break;
						case "dangerouslySetInnerHTML":
							if (i != null) throw Error("`dangerouslySetInnerHTML` does not make sense on <textarea>.");
							break;
						default: i !== a && F(e, t, o, i, r, a);
					}
					Ut(e, f, p);
					return;
				case "option":
					for (var m in n) if (f = n[m], n.hasOwnProperty(m) && f != null && !r.hasOwnProperty(m)) switch (m) {
						case "selected":
							e.selected = !1;
							break;
						default: F(e, t, m, null, r, f);
					}
					for (c in r) if (f = r[c], p = n[c], r.hasOwnProperty(c) && f !== p && (f != null || p != null)) switch (c) {
						case "selected":
							f !== p && (B = !0), e.selected = f && typeof f != "function" && typeof f != "symbol";
							break;
						default: F(e, t, c, f, r, p);
					}
					return;
				case "img":
				case "link":
				case "area":
				case "base":
				case "br":
				case "col":
				case "embed":
				case "hr":
				case "keygen":
				case "meta":
				case "param":
				case "source":
				case "track":
				case "wbr":
				case "menuitem":
					for (var h in n) f = n[h], n.hasOwnProperty(h) && f != null && !r.hasOwnProperty(h) && F(e, t, h, null, r, f);
					for (l in r) if (f = r[l], p = n[l], r.hasOwnProperty(l) && f !== p && (f != null || p != null)) switch (l) {
						case "children":
						case "dangerouslySetInnerHTML":
							if (f != null) throw Error(t + " is a void element tag and must neither have `children` nor use `dangerouslySetInnerHTML`.");
							break;
						default: F(e, t, l, f, r, p);
					}
					return;
				default: if (vn(t)) {
					for (var g in n) f = n[g], n.hasOwnProperty(g) && f !== void 0 && !r.hasOwnProperty(g) && Pd(e, t, g, void 0, r, f);
					for (u in r) f = r[u], p = n[u], !r.hasOwnProperty(u) || f === p || f === void 0 && p === void 0 || Pd(e, t, u, f, r, p);
					return;
				}
			}
			for (var _ in n) f = n[_], n.hasOwnProperty(_) && f != null && !r.hasOwnProperty(_) && F(e, t, _, null, r, f);
			for (d in r) f = r[d], p = n[d], !r.hasOwnProperty(d) || f === p || f == null && p == null || F(e, t, d, f, r, p);
		}
		function Ld(e) {
			switch (e) {
				case "class": return "className";
				case "for": return "htmlFor";
				default: return e;
			}
		}
		function Rd(e) {
			for (var t = {}, n = e.style, r = 0; r < n.length; r++) {
				var i = n[r];
				i === "view-transition-name" && Od(e) || (t[i] = n.getPropertyValue(i));
			}
			return t;
		}
		function zd(e, t, n) {
			if (t != null && typeof t != "object") console.error("The `style` prop expects a mapping from style properties to values, not a string. For example, style={{marginRight: spacing + 'em'}} when using JSX.");
			else {
				var r, i = r = "", a;
				for (a in t) if (t.hasOwnProperty(a)) {
					var o = t[a];
					o != null && typeof o != "boolean" && o !== "" && (a.indexOf("--") === 0 ? (Ue(o, a), r += i + a + ":" + ("" + o).trim()) : typeof o != "number" || o === 0 || Fg.has(a) ? (Ue(o, a), r += i + a.replace(Tg, "-$1").toLowerCase().replace(Eg, "-ms-") + ":" + ("" + o).trim()) : r += i + a.replace(Tg, "-$1").toLowerCase().replace(Eg, "-ms-") + ":" + o + "px", i = ";");
				}
				r ||= null, t = e.getAttribute("style"), t !== r && (r = Md(r), t = Md(t), t === r || t[t.length - 1] === ";" && Dd(e) || (n.style = Rd(e)));
			}
		}
		function Bd(e, t, n, r, i, a) {
			if (i.delete(n), e = e.getAttribute(n), e === null) switch (typeof r) {
				case "undefined":
				case "function":
				case "symbol":
				case "boolean": return;
			}
			else if (r != null) switch (typeof r) {
				case "function":
				case "symbol":
				case "boolean": break;
				default: if (He(r, t), e === "" + r) return;
			}
			Ed(t, e, r, a);
		}
		function Vd(e, t, n, r, i, a) {
			if (i.delete(n), e = e.getAttribute(n), e === null) {
				switch (typeof r) {
					case "function":
					case "symbol": return;
				}
				if (!r) return;
			} else switch (typeof r) {
				case "function":
				case "symbol": break;
				default: if (r) return;
			}
			Ed(t, e, r, a);
		}
		function Hd(e, t, n, r, i, a) {
			if (i.delete(n), e = e.getAttribute(n), e === null) switch (typeof r) {
				case "undefined":
				case "function":
				case "symbol": return;
			}
			else if (r != null) switch (typeof r) {
				case "function":
				case "symbol": break;
				default: if (He(r, n), e === "" + r) return;
			}
			Ed(t, e, r, a);
		}
		function Ud(e, t, n, r, i, a) {
			if (i.delete(n), e = e.getAttribute(n), e === null) switch (typeof r) {
				case "undefined":
				case "function":
				case "symbol":
				case "boolean": return;
				default: if (isNaN(r)) return;
			}
			else if (r != null) switch (typeof r) {
				case "function":
				case "symbol":
				case "boolean": break;
				default: if (!isNaN(r) && (He(r, t), e === "" + r)) return;
			}
			Ed(t, e, r, a);
		}
		function Wd(e, t, n, r, i, a) {
			if (i.delete(n), e = e.getAttribute(n), e === null) switch (typeof r) {
				case "undefined":
				case "function":
				case "symbol":
				case "boolean": return;
			}
			else if (r != null) switch (typeof r) {
				case "function":
				case "symbol":
				case "boolean": break;
				default: if (He(r, t), n = wn("" + r), e === n) return;
			}
			Ed(t, e, r, a);
		}
		function Gd(e, t, n, r) {
			for (var i = {}, a = /* @__PURE__ */ new Set(), o = e.attributes, s = 0; s < o.length; s++) switch (o[s].name.toLowerCase()) {
				case "value": break;
				case "checked": break;
				case "selected": break;
				case "vt-name":
				case "vt-update":
				case "vt-enter":
				case "vt-exit":
				case "vt-share":
				case "vt-parent-enter":
				case "vt-parent-exit": break;
				default: a.add(o[s].name);
			}
			if (vn(t)) {
				for (var c in n) if (n.hasOwnProperty(c)) {
					var l = n[c];
					if (l != null) {
						if (ng.hasOwnProperty(c)) typeof l != "function" && Ad(c, l);
						else if (!0 !== n.suppressHydrationWarning) switch (c) {
							case "children":
								typeof l != "string" && typeof l != "number" || Ed("children", e.textContent, l, i);
								continue;
							case "suppressContentEditableWarning":
							case "suppressHydrationWarning":
							case "defaultValue":
							case "defaultChecked":
							case "innerHTML":
							case "ref": continue;
							case "dangerouslySetInnerHTML":
								o = e.innerHTML, l = l ? l.__html : void 0, l != null && (l = jd(e, l), Ed(c, o, l, i));
								continue;
							case "style":
								a.delete(c), zd(e, l, i);
								continue;
							case "offsetParent":
							case "offsetTop":
							case "offsetLeft":
							case "offsetWidth":
							case "offsetHeight":
							case "isContentEditable":
							case "outerText":
							case "outerHTML":
								a.delete(c.toLowerCase()), console.error("Assignment to read-only property will result in a no-op: `%s`", c);
								continue;
							case "className":
								a.delete("class"), o = wt(e, "class", l), Ed("className", o, l, i);
								continue;
							default: r.context === _T && t !== "svg" && t !== "math" ? a.delete(c.toLowerCase()) : a.delete(c), o = wt(e, c, l), Ed(c, o, l, i);
						}
					}
				}
			} else for (l in n) if (n.hasOwnProperty(l) && (c = n[l], c != null)) {
				if (ng.hasOwnProperty(l)) typeof c != "function" && Ad(l, c);
				else if (!0 !== n.suppressHydrationWarning) switch (l) {
					case "children":
						typeof c != "string" && typeof c != "number" || Ed("children", e.textContent, c, i);
						continue;
					case "suppressContentEditableWarning":
					case "suppressHydrationWarning":
					case "value":
					case "checked":
					case "selected":
					case "defaultValue":
					case "defaultChecked":
					case "innerHTML":
					case "ref": continue;
					case "dangerouslySetInnerHTML":
						o = e.innerHTML, c = c ? c.__html : void 0, c != null && (c = jd(e, c), o !== c && (i[l] = { __html: o }));
						continue;
					case "className":
						Bd(e, l, "class", c, a, i);
						continue;
					case "tabIndex":
						Bd(e, l, "tabindex", c, a, i);
						continue;
					case "style":
						a.delete(l), zd(e, c, i);
						continue;
					case "multiple":
						a.delete(l), Ed(l, e.multiple, c, i);
						continue;
					case "muted":
						a.delete(l), Ed(l, e.muted, c, i);
						continue;
					case "autoFocus":
						a.delete("autofocus"), Ed(l, e.autofocus, c, i);
						continue;
					case "data": if (t !== "object") {
						a.delete(l), o = e.getAttribute("data"), Ed(l, o, c, i);
						continue;
					}
					case "src":
					case "href":
						if (!(c !== "" || t === "a" && l === "href" || t === "object" && l === "data")) {
							console.error(l === "src" ? "An empty string (\"\") was passed to the %s attribute. This may cause the browser to download the whole page again over the network. To fix this, either do not render the element at all or pass null to %s instead of an empty string." : "An empty string (\"\") was passed to the %s attribute. To fix this, either do not render the element at all or pass null to %s instead of an empty string.", l, l);
							continue;
						}
						Wd(e, l, l, c, a, i);
						continue;
					case "action":
					case "formAction":
						if (o = e.getAttribute(l), typeof c == "function") {
							a.delete(l.toLowerCase()), l === "formAction" ? (a.delete("name"), a.delete("formenctype"), a.delete("formmethod"), a.delete("formtarget")) : (a.delete("enctype"), a.delete("method"), a.delete("target"));
							continue;
						}
						if (o === tT) {
							a.delete(l.toLowerCase()), Ed(l, "function", c, i);
							continue;
						}
						Wd(e, l, l.toLowerCase(), c, a, i);
						continue;
					case "xlinkHref":
						Wd(e, l, "xlink:href", c, a, i);
						continue;
					case "contentEditable":
						Hd(e, l, "contenteditable", c, a, i);
						continue;
					case "spellCheck":
						Hd(e, l, "spellcheck", c, a, i);
						continue;
					case "draggable":
					case "autoReverse":
					case "externalResourcesRequired":
					case "focusable":
					case "preserveAlpha":
						Hd(e, l, l, c, a, i);
						continue;
					case "allowFullScreen":
					case "async":
					case "autoPlay":
					case "controls":
					case "credentialless":
					case "default":
					case "defer":
					case "disabled":
					case "disablePictureInPicture":
					case "disableRemotePlayback":
					case "formNoValidate":
					case "hidden":
					case "loop":
					case "noModule":
					case "noValidate":
					case "open":
					case "playsInline":
					case "readOnly":
					case "required":
					case "reversed":
					case "scoped":
					case "seamless":
					case "itemScope":
						Vd(e, l, l.toLowerCase(), c, a, i);
						continue;
					case "capture":
					case "download":
						a: {
							s = e;
							var u = o = l, d = i;
							if (a.delete(u), s = s.getAttribute(u), s === null) switch (typeof c) {
								case "undefined":
								case "function":
								case "symbol": break a;
								default: if (!1 === c) break a;
							}
							else if (c != null) switch (typeof c) {
								case "function":
								case "symbol": break;
								case "boolean":
									if (!0 === c && s === "") break a;
									break;
								default: if (He(c, o), s === "" + c) break a;
							}
							Ed(o, s, c, d);
						}
						continue;
					case "cols":
					case "rows":
					case "size":
					case "span":
						a: {
							if (s = e, u = o = l, d = i, a.delete(u), s = s.getAttribute(u), s === null) switch (typeof c) {
								case "undefined":
								case "function":
								case "symbol":
								case "boolean": break a;
								default: if (isNaN(c) || 1 > c) break a;
							}
							else if (c != null) switch (typeof c) {
								case "function":
								case "symbol":
								case "boolean": break;
								default: if (!(isNaN(c) || 1 > c) && (He(c, o), s === "" + c)) break a;
							}
							Ed(o, s, c, d);
						}
						continue;
					case "rowSpan":
						Ud(e, l, "rowspan", c, a, i);
						continue;
					case "start":
						Ud(e, l, l, c, a, i);
						continue;
					case "xHeight":
						Bd(e, l, "x-height", c, a, i);
						continue;
					case "xlinkActuate":
						Bd(e, l, "xlink:actuate", c, a, i);
						continue;
					case "xlinkArcrole":
						Bd(e, l, "xlink:arcrole", c, a, i);
						continue;
					case "xlinkRole":
						Bd(e, l, "xlink:role", c, a, i);
						continue;
					case "xlinkShow":
						Bd(e, l, "xlink:show", c, a, i);
						continue;
					case "xlinkTitle":
						Bd(e, l, "xlink:title", c, a, i);
						continue;
					case "xlinkType":
						Bd(e, l, "xlink:type", c, a, i);
						continue;
					case "xmlBase":
						Bd(e, l, "xml:base", c, a, i);
						continue;
					case "xmlLang":
						Bd(e, l, "xml:lang", c, a, i);
						continue;
					case "xmlSpace":
						Bd(e, l, "xml:space", c, a, i);
						continue;
					case "inert":
						c !== "" || Yw[l] || (Yw[l] = !0, console.error("Received an empty string for a boolean attribute `%s`. This will treat the attribute as if it were false. Either pass `false` to silence this warning, or pass `true` if you used an empty string in earlier versions of React to indicate this attribute is true.", l)), Vd(e, l, l, c, a, i);
						continue;
					default: if (!(2 < l.length) || l[0] !== "o" && l[0] !== "O" || l[1] !== "n" && l[1] !== "N") {
						s = yn(l), o = !1, r.context === _T && t !== "svg" && t !== "math" ? a.delete(s.toLowerCase()) : (u = l.toLowerCase(), u = zg.hasOwnProperty(u) && zg[u] || null, u !== null && u !== l && (o = !0, a.delete(u)), a.delete(s));
						a: if (u = e, d = s, s = c, St(d)) {
							if (u.hasAttribute(d)) u = d.toLowerCase() === "nonce" ? u.nonce : u.getAttribute(d), He(s, d), s = u === "" + s ? s : u;
							else {
								switch (typeof s) {
									case "function":
									case "symbol": break a;
									case "boolean": if (u = d.toLowerCase().slice(0, 5), u !== "data-" && u !== "aria-") break a;
								}
								s = s === void 0 ? void 0 : null;
							}
						} else s = void 0;
						o || Ed(l, s, c, i);
					}
				}
			}
			return 0 < a.size && !0 !== n.suppressHydrationWarning && kd(e, a, i), Object.keys(i).length === 0 ? null : i;
		}
		function Kd(e, t) {
			switch (e.length) {
				case 0: return "";
				case 1: return e[0];
				case 2: return e[0] + " " + t + " " + e[1];
				default: return e.slice(0, -1).join(", ") + ", " + t + " " + e[e.length - 1];
			}
		}
		function qd(e) {
			switch (e) {
				case "css":
				case "script":
				case "font":
				case "img":
				case "image":
				case "input":
				case "link": return !0;
				default: return !1;
			}
		}
		function Jd() {
			if (typeof performance.getEntriesByType == "function") {
				for (var e = 0, t = 0, n = performance.getEntriesByType("resource"), r = 0; r < n.length; r++) {
					var i = n[r], a = i.transferSize, o = i.initiatorType, s = i.duration;
					if (a && s && qd(o)) {
						for (o = 0, s = i.responseEnd, r += 1; r < n.length; r++) {
							var c = n[r], l = c.startTime;
							if (l > s) break;
							var u = c.transferSize, d = c.initiatorType;
							u && qd(d) && (c = c.responseEnd, o += u * (c < s ? 1 : (s - l) / (c - l)));
						}
						if (--r, t += 8 * (a + o) / (i.duration / 1e3), e++, 10 < e) break;
					}
				}
				if (0 < e) return t / e / 1e6;
			}
			return navigator.connection && (e = navigator.connection.downlink, typeof e == "number") ? e : 5;
		}
		function Yd(e) {
			return e.nodeType === 9 ? e : e.ownerDocument;
		}
		function Xd(e) {
			switch (e) {
				case Lg: return vT;
				case Ig: return yT;
				default: return _T;
			}
		}
		function Zd(e, t) {
			if (e === _T) switch (t) {
				case "svg": return vT;
				case "math": return yT;
				default: return _T;
			}
			return e === vT && t === "foreignObject" ? _T : e;
		}
		function Qd(e, t, n, r) {
			return n = Yd(n).createElement(e), n[Kh] = r, n[qh] = t, Fd(n, e, t), _t(n), n;
		}
		function $d(e) {
			if (e = e.type, typeof e != "string" || e === "" || (e = e.toLowerCase(), e === "module" || e === "importmap" || e === "speculationrules")) return !1;
			switch (e) {
				case "application/ecmascript":
				case "application/javascript":
				case "application/x-ecmascript":
				case "application/x-javascript":
				case "text/ecmascript":
				case "text/javascript":
				case "text/javascript1.0":
				case "text/javascript1.1":
				case "text/javascript1.2":
				case "text/javascript1.3":
				case "text/javascript1.4":
				case "text/javascript1.5":
				case "text/jscript":
				case "text/livescript":
				case "text/x-ecmascript":
				case "text/x-javascript": return !1;
			}
			return !0;
		}
		function ef(e, t) {
			return e === "textarea" || e === "noscript" || typeof t.children == "string" || typeof t.children == "number" || typeof t.children == "bigint" || typeof t.dangerouslySetInnerHTML == "object" && t.dangerouslySetInnerHTML !== null && t.dangerouslySetInnerHTML.__html != null;
		}
		function tf() {
			var e = window.event;
			return e && e.type === "popstate" ? e !== wT && (wT = e, !0) : (wT = null, !1);
		}
		function nf() {
			var e = window.event;
			return e && e !== TT ? e.type : null;
		}
		function rf() {
			var e = window.event;
			return e && e !== TT ? e.timeStamp : -1.1;
		}
		function af(e) {
			setTimeout(function() {
				throw e;
			});
		}
		function of(e, t, n) {
			switch (t) {
				case "button":
				case "input":
				case "select":
				case "textarea":
					n.autoFocus && e.focus();
					break;
				case "img": n.src ? e.src = n.src : n.srcSet && (e.srcset = n.srcSet);
			}
		}
		function sf() {}
		function cf(e, t, n, r) {
			Id(e, t, n, r), e[qh] = r;
		}
		function lf(e) {
			mn(e, "");
		}
		function uf(e, t, n) {
			e.nodeValue = n;
		}
		function df(e) {
			if (!e.__reactWarnedAboutChildrenConflict) {
				var t = e[qh] || null;
				if (t !== null) {
					var n = mt(e);
					n !== null && (typeof t.children == "string" || typeof t.children == "number" ? (e.__reactWarnedAboutChildrenConflict = !0, w(n, function() {
						console.error("Cannot use a ref on a React element as a container to `createRoot` or `createPortal` if that element also sets \"children\" text content using React. It should be a leaf with no children. Otherwise it's ambiguous which children should be used.");
					})) : t.dangerouslySetInnerHTML != null && (e.__reactWarnedAboutChildrenConflict = !0, w(n, function() {
						console.error("Cannot use a ref on a React element as a container to `createRoot` or `createPortal` if that element also sets \"dangerouslySetInnerHTML\" using React. It should be a leaf with no children. Otherwise it's ambiguous which children should be used.");
					})));
				}
			}
		}
		function ff(e) {
			return e === "head";
		}
		function pf(e, t) {
			e.removeChild(t);
		}
		function mf(e, t) {
			(e.nodeType === 9 ? e.body : e.nodeName === "HTML" ? e.ownerDocument.body : e).removeChild(t);
		}
		function hf(e, t) {
			var n = t, r = 0;
			do {
				var i = n.nextSibling;
				if (e.removeChild(n), i && i.nodeType === 8) {
					if (n = i.data, n === oT || n === iT) {
						if (r === 0) {
							e.removeChild(i), vm(t);
							return;
						}
						r--;
					} else if (n === aT || n === sT || n === cT || n === lT || n === rT) r++;
					else if (n === uT) yp(e.ownerDocument.documentElement);
					else if (n === fT) {
						n = e.ownerDocument.head, yp(n);
						for (var a = n.firstChild; a;) {
							var o = a.nextSibling, s = a.nodeName;
							a[$h] || s === "SCRIPT" || s === "STYLE" || s === "LINK" && a.rel.toLowerCase() === "stylesheet" || n.removeChild(a), a = o;
						}
					} else n === dT && yp(e.ownerDocument.body);
				}
				n = i;
			} while (n);
			vm(t);
		}
		function gf(e, t) {
			var n = e;
			e = 0;
			do {
				var r = n.nextSibling;
				if (n.nodeType === 1 ? t ? (n._stashedDisplay = n.style.display, n.style.display = "none") : (n.style.display = n._stashedDisplay || "", n.getAttribute("style") === "" && n.removeAttribute("style")) : n.nodeType === 3 && (t ? (n._stashedText = n.nodeValue, n.nodeValue = "") : n.nodeValue = n._stashedText || ""), r && r.nodeType === 8) {
					if (n = r.data, n === oT) {
						if (e === 0) break;
						e--;
					} else n !== aT && n !== sT && n !== cT && n !== lT || e++;
				}
				n = r;
			} while (n);
		}
		function _f(e) {
			gf(e, !0);
		}
		function vf(e) {
			e = e.style, typeof e.setProperty == "function" ? e.setProperty("display", "none", "important") : e.display = "none";
		}
		function yf(e) {
			e.nodeValue = "";
		}
		function bf(e) {
			gf(e, !1);
		}
		function xf(e, t) {
			t = t[gT], t = t != null && t.hasOwnProperty("display") ? t.display : null, e.style.display = t == null || typeof t == "boolean" ? "" : ("" + t).trim();
		}
		function Sf(e, t) {
			e.nodeValue = t;
		}
		function Cf(e) {
			for (var t = e.firstChild; t != null;) {
				if (t.nodeType === 1 && getComputedStyle(t).display === "block") {
					w(mt(t) || mt(e), function(e, t) {
						console.error("You're about to start a <ViewTransition> around a display: inline element <%s>, which itself has a display: block element <%s> inside it. This might trigger a bug in Safari which causes the View Transition to be skipped with a duplicate name error.\nhttps://bugs.webkit.org/show_bug.cgi?id=290923", e.toLocaleLowerCase(), t.toLocaleLowerCase());
					}, e.tagName, t.tagName);
					break;
				}
				if (t.firstChild != null) t = t.firstChild;
				else {
					if (t === e) break;
					for (; t.nextSibling == null && t.parentNode != null && t.parentNode !== e;) t = t.parentNode;
					t = t.nextSibling;
				}
			}
		}
		function wf(e, t, n) {
			if (t = CSS.escape(t) === t ? t : "r-" + btoa(t).replace(/=/g, ""), e.style.viewTransitionName = t, n != null && (e.style.viewTransitionClass = n), n = getComputedStyle(e), n.display === "inline") {
				if (t = e.getClientRects(), t.length === 1) var r = 1;
				else for (var i = r = 0; i < t.length; i++) {
					var a = t[i];
					0 < a.width && 0 < a.height && r++;
				}
				r === 1 ? (e = e.style, e.display = t.length === 1 ? "inline-block" : "block", e.marginTop = "-" + n.paddingTop, e.marginBottom = "-" + n.paddingBottom) : Cf(e);
			}
		}
		function Tf(e, t) {
			e = e.style, t = t[gT];
			var n = t == null ? null : t.hasOwnProperty("viewTransitionName") ? t.viewTransitionName : t.hasOwnProperty("view-transition-name") ? t["view-transition-name"] : null;
			e.viewTransitionName = n == null || typeof n == "boolean" ? "" : ("" + n).trim(), n = t == null ? null : t.hasOwnProperty("viewTransitionClass") ? t.viewTransitionClass : t.hasOwnProperty("view-transition-class") ? t["view-transition-class"] : null, e.viewTransitionClass = n == null || typeof n == "boolean" ? "" : ("" + n).trim(), e.display === "inline-block" && (t == null ? e.display = e.margin = "" : (n = t.display, e.display = n == null || typeof n == "boolean" ? "" : n, n = t.margin, n == null ? (n = t.hasOwnProperty("marginTop") ? t.marginTop : t["margin-top"], e.marginTop = n == null || typeof n == "boolean" ? "" : n, t = t.hasOwnProperty("marginBottom") ? t.marginBottom : t["margin-bottom"], e.marginBottom = t == null || typeof t == "boolean" ? "" : t) : e.margin = n));
		}
		function Ef(e, t, n) {
			return n = n.ownerDocument.defaultView, {
				rect: e,
				abs: t.position === "absolute" || t.position === "fixed",
				clip: t.clipPath !== "none" || t.overflow !== "visible" || t.filter !== "none" || t.mask !== "none" || t.mask !== "none" || t.borderRadius !== "0px",
				view: 0 <= e.bottom && 0 <= e.right && e.top <= n.innerHeight && e.left <= n.innerWidth
			};
		}
		function Df(e) {
			return Ef(e.getBoundingClientRect(), getComputedStyle(e), e);
		}
		function Of(e) {
			var t = e.getBoundingClientRect();
			t = new DOMRect(t.x + 2e4, t.y + 2e4, t.width, t.height);
			var n = getComputedStyle(e);
			return Ef(t, n, e);
		}
		function kf(e, t) {
			if (typeof e == "object" && e) switch (e.name) {
				case "TimeoutError": return Error("A ViewTransition timed out because a Navigation stalled. This can happen if a Navigation is blocked on React itself. Such as if it's resolved inside useEffect. This can be solved by moving the resolution to useLayoutEffect.", { cause: e });
				case "AbortError": return t ? null : Error("A ViewTransition was aborted early. This might be because you have other View Transition libraries on the page and only one can run at a time. To avoid this, use only React's built-in <ViewTransition> to coordinate.", { cause: e });
				case "InvalidStateError": if (e.message === "View transition was skipped because document visibility state is hidden." || e.message === "Skipping view transition because document visibility state has become hidden." || e.message === "Skipping view transition because viewport size changed." || e.message === "Transition was aborted because of invalid state") return null;
			}
			return e;
		}
		function Af(e) {
			return e.documentElement.clientHeight;
		}
		function jf(e) {
			this.addEventListener("load", e), this.addEventListener("error", e);
		}
		function Mf(e, t, n, r, i, a, o, s, c, l, u) {
			var d = t.nodeType === 9 ? t : t.ownerDocument;
			try {
				var f = d.startViewTransition({
					update: function() {
						var t = d.defaultView, n = t.navigation && t.navigation.transition, o = d.fonts.status;
						r();
						var s = [];
						if (o === "loaded" && (Af(d), d.fonts.status === "loading" && s.push(d.fonts.ready)), o = s.length, e !== null) for (var c = e.suspenseyImages, u = 0, f = 0; f < c.length; f++) {
							var p = c[f];
							if (!p.complete) {
								var m = p.getBoundingClientRect();
								if (0 < m.bottom && 0 < m.right && m.top < t.innerHeight && m.left < t.innerWidth) {
									if (u += zp(p), u > YT) {
										s.length = o;
										break;
									}
									p = new Promise(jf.bind(p)), s.push(p);
								}
							}
						}
						if (0 < s.length) return l(0 < o ? s.length > o ? "Waiting on Fonts and Images" : "Waiting on Fonts" : "Waiting on Images"), t = Promise.race([Promise.all(s), new Promise(function(e) {
							return setTimeout(e, MT);
						})]).then(i, i), (n ? Promise.allSettled([n.finished, t]) : t).then(a, a);
						if (i(), n) return n.finished.then(a, a);
						a();
					},
					types: n
				});
				d.__reactViewTransition = f;
				var p = [];
				return f.ready.then(function() {
					for (var e = d.documentElement.getAnimations({ subtree: !0 }), t = 0; t < e.length; t++) {
						var n = e[t], r = n.effect, i = r.pseudoElement;
						if (i != null && i.startsWith("::view-transition")) {
							p.push(n), n = r.getKeyframes();
							for (var a = i = void 0, s = !0, c = 0; c < n.length; c++) {
								var l = n[c], u = l.width;
								if (i === void 0) i = u;
								else if (i !== u) {
									s = !1;
									break;
								}
								if (u = l.height, a === void 0) a = u;
								else if (a !== u) {
									s = !1;
									break;
								}
								delete l.width, delete l.height, l.transform === "none" && delete l.transform;
							}
							s && i !== void 0 && a !== void 0 && (r.setKeyframes(n), s = getComputedStyle(r.target, r.pseudoElement), s.width !== i || s.height !== a) && (s = n[0], s.width = i, s.height = a, s = n[n.length - 1], s.width = i, s.height = a, r.setKeyframes(n));
						}
					}
					o();
				}, function(e) {
					d.__reactViewTransition === f && (d.__reactViewTransition = null);
					try {
						e = kf(e, !1), e !== null && c(e);
					} finally {
						r(), i(), o(), u();
					}
				}), f.finished.finally(function() {
					for (var e = 0; e < p.length; e++) p[e].cancel();
					d.__reactViewTransition === f && (d.__reactViewTransition = null), u(), s();
				}), f;
			} catch {
				return r(), i(), u(), o(), null;
			}
		}
		function Nf(e, t) {
			this._scope = document.documentElement, this._selector = "::view-transition-" + e + "(" + t + ")";
		}
		function Pf(e) {
			return {
				name: e,
				group: new Nf("group", e),
				imagePair: new Nf("image-pair", e),
				old: new Nf("old", e),
				new: new Nf("new", e)
			};
		}
		function Ff(e) {
			this._fragmentFiber = e, this._observers = this._eventListeners = null;
		}
		function If(e, t, n, r) {
			return de(e).addEventListener(t, n, r), !1;
		}
		function Lf(e, t, n, r) {
			return de(e).removeEventListener(t, n, r), !1;
		}
		function Rf(e) {
			return e != null && typeof e != "boolean" && (!0 === e.once || e.signal instanceof AbortSignal) ? {
				capture: e.capture,
				passive: e.passive
			} : e;
		}
		function zf(e) {
			return e == null ? "c=0" : typeof e == "boolean" ? "c=" + (e ? "1" : "0") : "c=" + (e.capture ? "1" : "0");
		}
		function Bf(e, t, n, r) {
			if (e.length === 0) return -1;
			r = zf(r);
			for (var i = 0; i < e.length; i++) {
				var a = e[i];
				if (a.type === t && a.listener === n && zf(a.optionsOrUseCapture) === r) return i;
			}
			return -1;
		}
		function Vf(e, t) {
			return e.tag !== 6 && (e = de(e), mp(e, t));
		}
		function Hf(e, t) {
			return t.push(e), !1;
		}
		function Uf(e, t) {
			return e.tag !== 6 && (e = de(e), e === t || e.contains(t) ? (t.blur(), !0) : !1);
		}
		function Wf(e, t) {
			return e.tag === 6 || (e = de(e), t.observe(e)), !1;
		}
		function Gf(e, t) {
			return e.tag === 6 || (e = de(e), t.unobserve(e)), !1;
		}
		function Kf(e, t, n) {
			NT.push({
				fragmentInstance: e,
				observer: t,
				instance: n
			}), PT || (PT = !0, hp(function() {
				PT = !1;
				var e = NT;
				NT = [];
				for (var t = 0; t < e.length; t++) {
					var n = e[t];
					n.observer.unobserve(n.instance);
				}
			}));
		}
		function qf(e, t) {
			if (e.tag === 6) {
				e = e.stateNode;
				var n = e.ownerDocument.createRange();
				n.selectNodeContents(e), t.push.apply(t, n.getClientRects());
			} else e = de(e), t.push.apply(t, e.getClientRects());
			return !1;
		}
		function Jf(e, t, n, r, i) {
			var a = pt(i);
			if (e & Node.DOCUMENT_POSITION_CONTAINED_BY) {
				if (n = !!a) a: {
					for (; a !== null;) {
						if (a.tag === 7 && (a === t || a.alternate === t)) {
							n = !0;
							break a;
						}
						a = a.return;
					}
					n = !1;
				}
				return n;
			}
			if (e & Node.DOCUMENT_POSITION_CONTAINS) {
				if (a === null) return a = i.ownerDocument, i === a || i === a.documentElement || i === a.body;
				a: {
					for (a = t, t = se(t); a !== null;) {
						if (!(a.tag !== 5 && a.tag !== 3 && a.tag !== 27 || a !== t && a.alternate !== t)) {
							a = !0;
							break a;
						}
						a = a.return;
					}
					a = !1;
				}
				return a;
			}
			return e & Node.DOCUMENT_POSITION_PRECEDING ? ((t = !!a) && !(t = a === n) && (t = he(n, a, me), t === null ? t = !1 : (oe(t, !0, fe, a, n), a = Em, Em = null, t = a !== null)), t) : e & Node.DOCUMENT_POSITION_FOLLOWING ? ((t = !!a) && !(t = a === r) && (t = he(r, a, me), t === null ? t = !1 : (oe(t, !0, pe, a, r), a = Em, Dm = Em = null, t = a !== null)), t) : !1;
		}
		function Yf(e, t) {
			var n = e.ownerDocument.createRange();
			n.selectNodeContents(e), e = n.getBoundingClientRect(), window.scrollTo(window.scrollX + e.left, t ? window.scrollY + e.top : window.scrollY + e.bottom - window.innerHeight);
		}
		function Xf(e, t) {
			return e = de(e), Zf(e, t), !1;
		}
		function Zf(e, t) {
			e.reactFragments ??= /* @__PURE__ */ new Set(), e.reactFragments.add(t);
		}
		function Qf(e, t) {
			var n = t._eventListeners;
			if (n !== null) for (var r = 0; r < n.length; r++) {
				var i = n[r];
				e.addEventListener(i.type, i.attachedListener, Rf(i.optionsOrUseCapture));
			}
			e.nodeType !== 3 && (n = t._observers, n !== null && n.forEach(function(n) {
				for (var r = 0, i = 0; i < NT.length; i++) {
					var a = NT[i];
					(a.fragmentInstance !== t || a.observer !== n || a.instance !== e) && (NT[r++] = a);
				}
				NT.length = r, n.observe(e);
			}), Zf(e, t));
		}
		function $f(e, t) {
			var n = t._eventListeners;
			if (n !== null) for (var r = 0; r < n.length; r++) {
				var i = n[r];
				e.removeEventListener(i.type, i.attachedListener, Rf(i.optionsOrUseCapture));
			}
			e.nodeType !== 3 && (n = t._observers, n !== null && n.forEach(function(n) {
				typeof n.rootMargin == "string" ? Kf(t, n, e) : n.unobserve(e);
			}), e.reactFragments != null && e.reactFragments.delete(t));
		}
		function ep(e) {
			var t = e.firstChild;
			for (t && t.nodeType === 10 && (t = t.nextSibling); t;) {
				var n = t;
				switch (t = t.nextSibling, n.nodeName) {
					case "HTML":
					case "HEAD":
					case "BODY":
						ep(n), ft(n);
						continue;
					case "SCRIPT":
					case "STYLE": continue;
					case "LINK": if (n.rel.toLowerCase() === "stylesheet") continue;
				}
				e.removeChild(n);
			}
		}
		function tp(e, t, n, r) {
			for (; e.nodeType === 1;) {
				var i = n;
				if (e.nodeName.toLowerCase() !== t.toLowerCase()) {
					if (!r && (e.nodeName !== "INPUT" || e.type !== "hidden")) break;
				} else if (!r) {
					if (t === "input" && e.type === "hidden") {
						He(i.name, "name");
						var a = i.name == null ? null : "" + i.name;
						if (i.type === "hidden" && e.getAttribute("name") === a) return e;
					} else return e;
				} else if (!e[$h]) switch (t) {
					case "meta":
						if (!e.hasAttribute("itemprop")) break;
						return e;
					case "link":
						if (a = e.getAttribute("rel"), a === "stylesheet" && e.hasAttribute("data-precedence") || a !== i.rel || e.getAttribute("href") !== (i.href == null || i.href === "" ? null : i.href) || e.getAttribute("crossorigin") !== (i.crossOrigin == null ? null : i.crossOrigin) || e.getAttribute("title") !== (i.title == null ? null : i.title)) break;
						return e;
					case "style":
						if (e.hasAttribute("data-precedence")) break;
						return e;
					case "script":
						if (a = e.getAttribute("src"), (a !== (i.src == null ? null : i.src) || e.getAttribute("type") !== (i.type == null ? null : i.type) || e.getAttribute("crossorigin") !== (i.crossOrigin == null ? null : i.crossOrigin)) && a && e.hasAttribute("async") && !e.hasAttribute("itemprop")) break;
						return e;
					default: return e;
				}
				if (e = op(e.nextSibling), e === null) break;
			}
			return null;
		}
		function np(e, t, n) {
			if (t === "") return null;
			for (; e.nodeType !== 3;) if ((e.nodeType !== 1 || e.nodeName !== "INPUT" || e.type !== "hidden") && !n || (e = op(e.nextSibling), e === null)) return null;
			return e;
		}
		function rp(e, t) {
			for (; e.nodeType !== 8;) if ((e.nodeType !== 1 || e.nodeName !== "INPUT" || e.type !== "hidden") && !t || (e = op(e.nextSibling), e === null)) return null;
			return e;
		}
		function I(e) {
			return e.data === sT || e.data === cT;
		}
		function ip(e) {
			return e.data === lT || e.data === sT && e.ownerDocument.readyState !== hT;
		}
		function ap(e, t) {
			var n = e.ownerDocument;
			if (e.data === cT) e._reactRetry = t;
			else if (e.data !== sT || n.readyState !== hT) t();
			else {
				var r = function() {
					t(), n.removeEventListener("DOMContentLoaded", r);
				};
				n.addEventListener("DOMContentLoaded", r), e._reactRetry = r;
			}
		}
		function op(e) {
			for (; e != null; e = e.nextSibling) {
				var t = e.nodeType;
				if (t === 1 || t === 3) break;
				if (t === 8) {
					if (t = e.data, t === aT || t === lT || t === sT || t === cT || t === rT || t === pT || t === mT) break;
					if (t === oT || t === iT) return null;
				}
			}
			return e;
		}
		function sp(e) {
			if (e.nodeType === 1) {
				for (var t = e.nodeName.toLowerCase(), n = {}, r = e.attributes, i = 0; i < r.length; i++) {
					var a = r[i];
					n[Ld(a.name)] = a.name.toLowerCase() === "style" ? Rd(e) : a.value;
				}
				return {
					type: t,
					props: n
				};
			}
			return e.nodeType === 8 ? e.data === rT ? {
				type: "Activity",
				props: {}
			} : {
				type: "Suspense",
				props: {}
			} : e.nodeValue;
		}
		function cp(e, t, n) {
			return n === null || !0 !== n[nT] ? (e.nodeValue === t ? e = null : (t = Md(t), e = Md(e.nodeValue) === t ? null : e.nodeValue), e) : null;
		}
		function lp(e) {
			e = e.nextSibling;
			for (var t = 0; e;) {
				if (e.nodeType === 8) {
					var n = e.data;
					if (n === oT || n === iT) {
						if (t === 0) return op(e.nextSibling);
						t--;
					} else n !== aT && n !== lT && n !== sT && n !== cT && n !== rT || t++;
				}
				e = e.nextSibling;
			}
			return null;
		}
		function up(e) {
			e = e.previousSibling;
			for (var t = 0; e;) {
				if (e.nodeType === 8) {
					var n = e.data;
					if (n === aT || n === lT || n === sT || n === cT || n === rT) {
						if (t === 0) return e;
						t--;
					} else n !== oT && n !== iT || t++;
				}
				e = e.previousSibling;
			}
			return null;
		}
		function dp(e) {
			vm(e);
		}
		function fp(e) {
			vm(e);
		}
		function pp(e) {
			vm(e);
		}
		function mp(e, t) {
			function n() {
				r = !0;
			}
			if (e.ownerDocument.activeElement === e) return !0;
			var r = !1;
			try {
				e.ownerDocument.addEventListener("focus", n, !0), (e.focus || HTMLElement.prototype.focus).call(e, t);
			} finally {
				e.ownerDocument.removeEventListener("focus", n, !0);
			}
			return r;
		}
		function hp(e) {
			AT(function() {
				AT(function(t) {
					return e(t);
				});
			});
		}
		function gp(e, t, n, r, i) {
			switch (i && fn(e, r.ancestorInfo), t = Yd(n), e) {
				case "html":
					if (e = t.documentElement, !e) throw Error("React expected an <html> element (document.documentElement) to exist in the Document but one was not found. React never removes the documentElement for any Document it renders into so the cause is likely in some other script running on this page.");
					return e;
				case "head":
					if (e = t.head, !e) throw Error("React expected a <head> element (document.head) to exist in the Document but one was not found. React never removes the head for any Document it renders into so the cause is likely in some other script running on this page.");
					return e;
				case "body":
					if (e = t.body, !e) throw Error("React expected a <body> element (document.body) to exist in the Document but one was not found. React never removes the body for any Document it renders into so the cause is likely in some other script running on this page.");
					return e;
				default: throw Error("resolveSingletonInstance was called with an element type that is not supported. This is a bug in React.");
			}
		}
		function _p(e, t, n, r) {
			if (!n[Jh] && mt(n)) {
				var i = n.tagName.toLowerCase();
				console.error("You are mounting a new %s component when a previous one has not first unmounted. It is an error to render more than one %s component at a time and attributes and children of these components will likely fail in unpredictable ways. Please only render a single instance of <%s> and if you need to mount a new one, ensure any previous ones have unmounted first.", i, i, i);
			}
			switch (e) {
				case "html":
				case "head":
				case "body": break;
				default: console.error("acquireSingletonInstance was called with an element type that is not supported. This is a bug in React.");
			}
			for (i = n.attributes; i.length;) n.removeAttributeNode(i[0]);
			Fd(n, e, t), n[Kh] = r, n[qh] = t;
		}
		function vp(e, t, n) {
			for (var r in n) {
				var i = n[r];
				n.hasOwnProperty(r) && i != null && F(e, t, r, null, eT, i);
			}
			n.dangerouslySetInnerHTML != null && (e.textContent = ""), e.onclick === Tn && (e.onclick = null), ft(e);
		}
		function yp(e) {
			for (var t = e.attributes; t.length;) e.removeAttributeNode(t[0]);
			ft(e);
		}
		function bp(e) {
			if (typeof e.getRootNode == "function") {
				var t = e.getRootNode();
				if (t.nodeType === 9 || t.nodeType === 11) return t;
			}
			return e.nodeType === 9 ? e : e.ownerDocument;
		}
		function xp(e, t, n) {
			var r = WT;
			if (r && typeof t == "string" && t) {
				var i = Nt(t);
				i = "link[rel=\"" + e + "\"][href=\"" + i + "\"]", typeof n == "string" && (i += "[crossorigin=\"" + n + "\"]"), HT.has(i) || (HT.add(i), e = {
					rel: e,
					crossOrigin: n,
					href: t
				}, r.querySelector(i) === null && (t = r.createElement("link"), Fd(t, "link", e), _t(t), r.head.appendChild(t)));
			}
		}
		function Sp(e, t, n, r) {
			var i = (i = th.current) ? bp(i) : null;
			if (!i) throw Error("\"resourceRoot\" was expected to exist. This is a bug in React.");
			switch (e) {
				case "meta":
				case "title": return null;
				case "style": return typeof n.precedence == "string" && typeof n.href == "string" ? (n = wp(n.href), t = gt(i).hoistableStyles, r = t.get(n), r || (r = {
					type: "style",
					instance: null,
					count: 0,
					state: null
				}, t.set(n, r)), r) : {
					type: "void",
					instance: null,
					count: 0,
					state: null
				};
				case "link":
					if (n.rel === "stylesheet" && typeof n.href == "string" && typeof n.precedence == "string") {
						e = wp(n.href);
						var a = gt(i).hoistableStyles, o = a.get(e);
						if (o || (i = i.ownerDocument || i, o = {
							type: "stylesheet",
							instance: null,
							count: 0,
							state: {
								loading: IT,
								preload: null
							}
						}, a.set(e, o), (a = i.querySelector(Tp(e))) ? a._p || (o.instance = a, o.state.loading = LT | BT) : (a = VT.get(e), a || (a = {
							rel: "preload",
							as: "style",
							href: n.href,
							crossOrigin: n.crossOrigin,
							integrity: n.integrity,
							media: n.media,
							hrefLang: n.hrefLang,
							referrerPolicy: n.referrerPolicy
						}, VT.set(e, a)), Dp(i, e, a, o.state))), t && r === null) throw n = "\n\n  - " + Cp(t) + "\n  + " + Cp(n), Error("Expected <link> not to update to be updated to a stylesheet with precedence. Check the `rel`, `href`, and `precedence` props of this component. Alternatively, check whether two different <link> components render in the same slot or share the same key." + n);
						return o;
					}
					if (t && r !== null) throw n = "\n\n  - " + Cp(t) + "\n  + " + Cp(n), Error("Expected stylesheet with precedence to not be updated to a different kind of <link>. Check the `rel`, `href`, and `precedence` props of this component. Alternatively, check whether two different <link> components render in the same slot or share the same key." + n);
					return null;
				case "script": return t = n.async, n = n.src, typeof n == "string" && t && typeof t != "function" && typeof t != "symbol" ? (n = Op(n), t = gt(i).hoistableScripts, r = t.get(n), r || (r = {
					type: "script",
					instance: null,
					count: 0,
					state: null
				}, t.set(n, r)), r) : {
					type: "void",
					instance: null,
					count: 0,
					state: null
				};
				default: throw Error("getResource encountered a type it did not expect: \"" + e + "\". this is a bug in React.");
			}
		}
		function Cp(e) {
			var t = 0, n = "<link";
			return typeof e.rel == "string" ? (t++, n += " rel=\"" + e.rel + "\"") : _h.call(e, "rel") && (t++, n += " rel=\"" + (e.rel === null ? "null" : "invalid type " + typeof e.rel) + "\""), typeof e.href == "string" ? (t++, n += " href=\"" + e.href + "\"") : _h.call(e, "href") && (t++, n += " href=\"" + (e.href === null ? "null" : "invalid type " + typeof e.href) + "\""), typeof e.precedence == "string" ? (t++, n += " precedence=\"" + e.precedence + "\"") : _h.call(e, "precedence") && (t++, n += " precedence={" + (e.precedence === null ? "null" : "invalid type " + typeof e.precedence) + "}"), Object.getOwnPropertyNames(e).length > t && (n += " ..."), n + " />";
		}
		function wp(e) {
			return "href=\"" + Nt(e) + "\"";
		}
		function Tp(e) {
			return "link[rel=\"stylesheet\"][" + e + "]";
		}
		function Ep(e) {
			return L({}, e, {
				"data-precedence": e.precedence,
				precedence: null
			});
		}
		function Dp(e, t, n, r) {
			if (t = e.querySelector("link[rel=\"preload\"][as=\"style\"][" + t + "]")) {
				if (!0 !== t[eg]) {
					r.loading = LT;
					return;
				}
			} else t = e.createElement("link"), t[eg] = !0, t.onload = t.onerror = vt.bind(null, t), Fd(t, "link", n), _t(t), e.head.appendChild(t);
			r.preload = t, t.addEventListener("load", function() {
				return r.loading |= LT;
			}), t.addEventListener("error", function() {
				return r.loading |= RT;
			});
		}
		function Op(e) {
			return "[src=\"" + Nt(e) + "\"]";
		}
		function kp(e) {
			return "script[async]" + e;
		}
		function Ap(e, t, n) {
			if (t.count++, t.instance === null) switch (t.type) {
				case "style":
					var r = e.querySelector("style[data-href~=\"" + Nt(n.href) + "\"]");
					if (r) return t.instance = r, _t(r), r;
					var i = L({}, n, {
						"data-href": n.href,
						"data-precedence": n.precedence,
						href: null,
						precedence: null
					});
					return r = (e.ownerDocument || e).createElement("style"), _t(r), Fd(r, "style", i), jp(r, n.precedence, e), t.instance = r;
				case "stylesheet":
					i = wp(n.href);
					var a = e.querySelector(Tp(i));
					if (a) return t.state.loading |= BT, t.instance = a, _t(a), a;
					r = Ep(n), (i = VT.get(i)) && Mp(r, i), a = (e.ownerDocument || e).createElement("link"), _t(a);
					var o = a;
					return o._p = new Promise(function(e, t) {
						o.onload = e, o.onerror = t;
					}), Fd(a, "link", r), t.state.loading |= BT, jp(a, n.precedence, e), t.instance = a;
				case "script": return a = Op(n.src), (i = e.querySelector(kp(a))) ? (t.instance = i, _t(i), i) : (r = n, (i = VT.get(a)) && (r = L({}, n), Np(r, i)), e = e.ownerDocument || e, i = e.createElement("script"), _t(i), Fd(i, "link", r), e.head.appendChild(i), t.instance = i);
				case "void": return null;
				default: throw Error("acquireResource encountered a resource type it did not expect: \"" + t.type + "\". this is a bug in React.");
			}
			else t.type === "stylesheet" && (t.state.loading & BT) === IT && (r = t.instance, t.state.loading |= BT, jp(r, n.precedence, e));
			return t.instance;
		}
		function jp(e, t, n) {
			for (var r = n.querySelectorAll("link[rel=\"stylesheet\"][data-precedence],style[data-precedence]"), i = r.length ? r[r.length - 1] : null, a = i, o = 0; o < r.length; o++) {
				var s = r[o];
				if (s.dataset.precedence === t) a = s;
				else if (a !== i) break;
			}
			a ? a.parentNode.insertBefore(e, a.nextSibling) : (t = n.nodeType === 9 ? n.head : n, t.insertBefore(e, t.firstChild));
		}
		function Mp(e, t) {
			e.crossOrigin ??= t.crossOrigin, e.referrerPolicy ??= t.referrerPolicy, e.title ??= t.title;
		}
		function Np(e, t) {
			e.crossOrigin ??= t.crossOrigin, e.referrerPolicy ??= t.referrerPolicy, e.integrity ??= t.integrity;
		}
		function Pp(e, t, n) {
			if (GT === null) {
				var r = /* @__PURE__ */ new Map(), i = GT = /* @__PURE__ */ new Map();
				i.set(n, r);
			} else i = GT, r = i.get(n), r || (r = /* @__PURE__ */ new Map(), i.set(n, r));
			if (r.has(e)) return r;
			for (r.set(e, null), n = n.getElementsByTagName(e), i = 0; i < n.length; i++) {
				var a = n[i];
				if (!(a[$h] || a[Kh] || e === "link" && a.getAttribute("rel") === "stylesheet") && a.namespaceURI !== Lg) {
					var o = a.getAttribute(t) || "";
					o = e + o;
					var s = r.get(o);
					s ? s.push(a) : r.set(o, [a]);
				}
			}
			return r;
		}
		function Fp(e, t, n) {
			e = e.ownerDocument || e, e.head.insertBefore(n, t === "title" ? e.querySelector("head > title") : null);
		}
		function Ip(e, t, n) {
			var r = !n.ancestorInfo.containerTagInScope;
			if (n.context === vT || t.itemProp != null) return !r || t.itemProp == null || e !== "meta" && e !== "title" && e !== "style" && e !== "link" && e !== "script" || console.error("Cannot render a <%s> outside the main document if it has an `itemProp` prop. `itemProp` suggests the tag belongs to an `itemScope` which can appear anywhere in the DOM. If you were intending for React to hoist this <%s> remove the `itemProp` prop. Otherwise, try moving this tag into the <head> or <body> of the Document.", e, e), !1;
			switch (e) {
				case "meta":
				case "title": return !0;
				case "style":
					if (typeof t.precedence != "string" || typeof t.href != "string" || t.href === "") {
						r && console.error("Cannot render a <style> outside the main document without knowing its precedence and a unique href key. React can hoist and deduplicate <style> tags if you provide a `precedence` prop along with an `href` prop that does not conflict with the `href` values used in any other hoisted <style> or <link rel=\"stylesheet\" ...> tags.  Note that hoisting <style> tags is considered an advanced feature that most will not use directly. Consider moving the <style> tag to the <head> or consider adding a `precedence=\"default\"` and `href=\"some unique resource identifier\"`.");
						break;
					}
					return !0;
				case "link":
					if (typeof t.rel != "string" || typeof t.href != "string" || t.href === "" || t.onLoad || t.onError) {
						if (t.rel === "stylesheet" && typeof t.precedence == "string") {
							e = t.href;
							var i = t.onError, a = t.disabled;
							n = [], t.onLoad && n.push("`onLoad`"), i && n.push("`onError`"), a != null && n.push("`disabled`"), i = Kd(n, "and"), i += n.length === 1 ? " prop" : " props", a = n.length === 1 ? "an " + i : "the " + i, n.length && console.error("React encountered a <link rel=\"stylesheet\" href=\"%s\" ... /> with a `precedence` prop that also included %s. The presence of loading and error handlers indicates an intent to manage the stylesheet loading state from your from your Component code and React will not hoist or deduplicate this stylesheet. If your intent was to have React hoist and deduplciate this stylesheet using the `precedence` prop remove the %s, otherwise remove the `precedence` prop.", e, a, i);
						}
						r && (typeof t.rel != "string" || typeof t.href != "string" || t.href === "" ? console.error("Cannot render a <link> outside the main document without a `rel` and `href` prop. Try adding a `rel` and/or `href` prop to this <link> or moving the link into the <head> tag") : (t.onError || t.onLoad) && console.error("Cannot render a <link> with onLoad or onError listeners outside the main document. Try removing onLoad={...} and onError={...} or moving it into the root <head> tag or somewhere in the <body>."));
						break;
					}
					switch (t.rel) {
						case "stylesheet": return e = t.precedence, t = t.disabled, typeof e != "string" && r && console.error("Cannot render a <link rel=\"stylesheet\" /> outside the main document without knowing its precedence. Consider adding precedence=\"default\" or moving it into the root <head> tag."), typeof e == "string" && t == null;
						default: return !0;
					}
				case "script":
					if (e = t.async && typeof t.async != "function" && typeof t.async != "symbol", !e || t.onLoad || t.onError || !t.src || typeof t.src != "string") {
						r && (e ? t.onLoad || t.onError ? console.error("Cannot render a <script> with onLoad or onError listeners outside the main document. Try removing onLoad={...} and onError={...} or moving it into the root <head> tag or somewhere in the <body>.") : console.error("Cannot render a <script> outside the main document without `async={true}` and a non-empty `src` prop. Ensure there is a valid `src` and either make the script async or move it into the root <head> tag or somewhere in the <body>.") : console.error("Cannot render a sync or defer <script> outside the main document without knowing its order. Try adding async=\"\" or moving it into the root <head> tag."));
						break;
					}
					return !0;
				case "noscript":
				case "template": r && console.error("Cannot render <%s> outside the main document. Try moving it into the root <head> tag.", e);
			}
			return !1;
		}
		function Lp(e, t) {
			return e === "img" && t.src != null && t.src !== "" && t.onLoad == null && t.loading !== "lazy";
		}
		function Rp(e) {
			return e.type !== "stylesheet" || (e.state.loading & zT) !== IT;
		}
		function zp(e) {
			return (e.width || 100) * (e.height || 100) * (typeof devicePixelRatio == "number" ? devicePixelRatio : 1) * .25;
		}
		function Bp(e, t) {
			typeof t.decode == "function" && (e.imgCount++, t.complete || (e.imgBytes += zp(t), e.suspenseyImages.push(t)), e = Gp.bind(e), t.decode().then(e, e));
		}
		function Vp(e, t, n, r) {
			if (n.type === "stylesheet" && (typeof r.media != "string" || !1 !== matchMedia(r.media).matches) && (n.state.loading & BT) === IT) {
				if (n.instance === null) {
					var i = wp(r.href), a = t.querySelector(Tp(i));
					if (a) {
						t = a._p, typeof t == "object" && t && typeof t.then == "function" && (e.count++, e = Wp.bind(e), t.then(e, e)), n.state.loading |= BT, n.instance = a, _t(a);
						return;
					}
					a = t.ownerDocument || t, r = Ep(r), (i = VT.get(i)) && Mp(r, i), a = a.createElement("link"), _t(a);
					var o = a;
					o._p = new Promise(function(e, t) {
						o.onload = e, o.onerror = t;
					}), Fd(a, "link", r), n.instance = a;
				}
				e.stylesheets === null && (e.stylesheets = /* @__PURE__ */ new Map()), e.stylesheets.set(n, t), (t = n.state.preload) && (n.state.loading & zT) === IT && (e.count++, n = Wp.bind(e), t.addEventListener("load", n), t.addEventListener("error", n));
			}
		}
		function Hp(e, t) {
			return e.stylesheets && e.count === 0 && Kp(e, e.stylesheets), 0 < e.count || 0 < e.imgCount ? function(n) {
				var r = setTimeout(function() {
					if (e.stylesheets && Kp(e, e.stylesheets), e.unsuspend) {
						var t = e.unsuspend;
						e.unsuspend = null, t();
					}
				}, KT + t);
				0 < e.imgBytes && YT === 0 && (YT = 125 * Jd() * JT);
				var i = setTimeout(function() {
					if (e.waitingForImages = !1, e.count === 0 && (e.stylesheets && Kp(e, e.stylesheets), e.unsuspend)) {
						var t = e.unsuspend;
						e.unsuspend = null, t();
					}
				}, (e.imgBytes > YT ? 50 : qT) + t);
				return e.unsuspend = n, function() {
					e.unsuspend = null, clearTimeout(r), clearTimeout(i);
				};
			} : null;
		}
		function Up(e) {
			if (e.count === 0 && (e.imgCount === 0 || !e.waitingForImages)) {
				if (e.stylesheets) Kp(e, e.stylesheets);
				else if (e.unsuspend) {
					var t = e.unsuspend;
					e.unsuspend = null, t();
				}
			}
		}
		function Wp() {
			this.count--, Up(this);
		}
		function Gp() {
			this.imgCount--, Up(this);
		}
		function Kp(e, t) {
			e.stylesheets = null, e.unsuspend !== null && (e.count++, ZT = /* @__PURE__ */ new Map(), t.forEach(qp, e), ZT = null, Wp.call(e));
		}
		function qp(e, t) {
			if (!(t.state.loading & BT)) {
				var n = ZT.get(e);
				if (n) var r = n.get(XT);
				else {
					n = /* @__PURE__ */ new Map(), ZT.set(e, n);
					for (var i = e.querySelectorAll("link[data-precedence],style[data-precedence]"), a = 0; a < i.length; a++) {
						var o = i[a];
						(o.nodeName === "LINK" || o.getAttribute("media") !== "not all") && (n.set(o.dataset.precedence, o), r = o);
					}
					r && n.set(XT, r);
				}
				i = t.instance, o = i.getAttribute("data-precedence"), a = n.get(o) || r, a === r && n.set(XT, i), n.set(o, i), this.count++, r = Wp.bind(this), i.addEventListener("load", r), i.addEventListener("error", r), a ? a.parentNode.insertBefore(i, a.nextSibling) : (e = e.nodeType === 9 ? e.head : e, e.insertBefore(i, e.firstChild)), t.state.loading |= BT;
			}
		}
		function Jp(e, t, n, r, i, a, o, s, c) {
			for (this.tag = 1, this.containerInfo = e, this.pingCache = this.current = this.pendingChildren = null, this.timeoutHandle = OT, this.callbackNode = this.next = this.pendingContext = this.context = this.cancelPendingCommit = null, this.callbackPriority = 0, this.expirationTimes = et(-1), this.entangledLanes = this.shellSuspendCounter = this.errorRecoveryDisabledLanes = this.expiredLanes = this.warmLanes = this.pingedLanes = this.suspendedLanes = this.pendingLanes = 0, this.entanglements = et(0), this.hiddenUpdates = et(null), this.identifierPrefix = r, this.onUncaughtError = i, this.onCaughtError = a, this.onRecoverableError = o, this.pooledCache = null, this.pooledCacheLanes = 0, this.formState = c, this.transitionTypes = null, this.incompleteTransitions = /* @__PURE__ */ new Map(), this.passiveEffectDuration = this.effectDuration = -0, this.memoizedUpdaters = /* @__PURE__ */ new Set(), e = this.pendingUpdatersLaneMap = [], t = 0; 31 > t; t++) e.push(/* @__PURE__ */ new Set());
			this._debugRootType = n ? "hydrateRoot()" : "createRoot()";
		}
		function Yp(e, t, n, r, i, a, o, s, c, l, u, d) {
			return e = new Jp(e, t, n, o, c, l, u, d, s), t = Vv, !0 === a && (t |= Hv | Uv), t |= W, a = g(3, null, null, t), e.current = a, a.stateNode = e, t = Oi(), ki(t), e.pooledCache = t, ki(t), a.memoizedState = {
				element: r,
				isDehydrated: n,
				cache: t
			}, Ca(a), e;
		}
		function Xp(e) {
			return e ? (e = Rv, e) : Rv;
		}
		function Zp(e, t, n, r, i, a) {
			if (Mh && typeof Mh.onScheduleFiberRoot == "function") try {
				Mh.onScheduleFiberRoot(jh, r, n);
			} catch (e) {
				Nh || (Nh = !0, console.error("React instrumentation encountered an error: %o", e));
			}
			i = Xp(i), r.context === null ? r.context = i : r.pendingContext = i, gh && hh !== null && !aE && (aE = !0, console.error("Render methods should be a pure function of props and state; triggering nested component updates from render is not allowed. If necessary, trigger nested updates in componentDidUpdate.\n\nCheck the render method of %s.", S(hh) || "Unknown")), r = Ta(t), r.payload = { element: n }, a = a === void 0 ? null : a, a !== null && (typeof a != "function" && console.error("Expected the last optional `callback` argument to be a function. Instead received: %s.", a), r.callback = a), n = Ea(e, r, t), n !== null && (Ni(t, "root.render()", null), su(n, e, t), Da(n, e, t));
		}
		function Qp(e, t) {
			if (e = e.memoizedState, e !== null && e.dehydrated !== null) {
				var n = e.retryLane;
				e.retryLane = n !== 0 && n < t ? n : t;
			}
		}
		function $p(e, t) {
			Qp(e, t), (e = e.alternate) && Qp(e, t);
		}
		function em(e) {
			if (e.tag === 13 || e.tag === 31) {
				var t = Fr(e, 67108864);
				t !== null && su(t, e, 67108864), $p(e, 67108864);
			}
		}
		function tm(e) {
			if (e.tag === 13 || e.tag === 31) {
				var t = iu(e);
				t = ot(t);
				var n = Fr(e, t);
				n !== null && su(n, e, t), $p(e, t);
			}
		}
		function nm() {
			return hh;
		}
		function rm(e, t, n, r) {
			var i = R.T;
			R.T = null;
			var a = z.p;
			try {
				z.p = Vh, am(e, t, n, r);
			} finally {
				z.p = a, R.T = i;
			}
		}
		function im(e, t, n, r) {
			var i = R.T;
			R.T = null;
			var a = z.p;
			try {
				z.p = Hh, am(e, t, n, r);
			} finally {
				z.p = a, R.T = i;
			}
		}
		function am(e, t, n, r) {
			if (gE) {
				var i = om(r);
				if (i === null) bd(e, t, r, _E, n), lm(e, r);
				else if (dm(i, e, t, n, r)) r.stopPropagation();
				else if (lm(e, r), t & 4 && -1 < TE.indexOf(e)) {
					for (; i !== null;) {
						var a = mt(i);
						if (a !== null) switch (a.tag) {
							case 3:
								if (a = a.stateNode, a.current.memoizedState.isDehydrated) {
									var o = Je(a.pendingLanes);
									if (o !== 0) {
										var s = a;
										for (s.pendingLanes |= 2, s.entangledLanes |= 2; o;) {
											var c = 1 << 31 - Fh(o);
											s.entanglements[1] |= c, o &= ~c;
										}
										ad(a), (Z & (iC | aC)) === rC && (HC = Sh() + UC, od(0, !1));
									}
								}
								break;
							case 31:
							case 13: s = Fr(a, 2), s !== null && su(s, a, 2), fu(), $p(a, 2);
						}
						if (a = om(r), a === null && bd(e, t, r, _E, n), a === i) break;
						i = a;
					}
					i !== null && r.stopPropagation();
				} else bd(e, t, r, null, n);
			}
		}
		function om(e) {
			return e = En(e), sm(e);
		}
		function sm(e) {
			if (_E = null, e = pt(e), e !== null) {
				var t = ee(e);
				if (t === null) e = null;
				else {
					var n = t.tag;
					if (n === 13) {
						if (e = te(t), e !== null) return e;
						e = null;
					} else if (n === 31) {
						if (e = ne(t), e !== null) return e;
						e = null;
					} else if (n === 3) {
						if (t.stateNode.current.memoizedState.isDehydrated) return t.tag === 3 ? t.stateNode.containerInfo : null;
						e = null;
					} else t !== e && (e = null);
				}
			}
			return _E = e, null;
		}
		function cm(e) {
			switch (e) {
				case "beforetoggle":
				case "cancel":
				case "click":
				case "close":
				case "contextmenu":
				case "copy":
				case "cut":
				case "auxclick":
				case "dblclick":
				case "dragend":
				case "dragstart":
				case "drop":
				case "focusin":
				case "focusout":
				case "input":
				case "invalid":
				case "keydown":
				case "keypress":
				case "keyup":
				case "mousedown":
				case "mouseup":
				case "paste":
				case "pause":
				case "play":
				case "pointercancel":
				case "pointerdown":
				case "pointerup":
				case "ratechange":
				case "reset":
				case "seeked":
				case "submit":
				case "toggle":
				case "touchcancel":
				case "touchend":
				case "touchstart":
				case "volumechange":
				case "change":
				case "selectionchange":
				case "textInput":
				case "compositionstart":
				case "compositionend":
				case "compositionupdate":
				case "beforeblur":
				case "afterblur":
				case "beforeinput":
				case "blur":
				case "fullscreenchange":
				case "fullscreenerror":
				case "focus":
				case "hashchange":
				case "popstate":
				case "select":
				case "selectstart": return Vh;
				case "drag":
				case "dragenter":
				case "dragexit":
				case "dragleave":
				case "dragover":
				case "mousemove":
				case "mouseout":
				case "mouseover":
				case "pointermove":
				case "pointerout":
				case "pointerover":
				case "resize":
				case "scroll":
				case "touchmove":
				case "wheel":
				case "mouseenter":
				case "mouseleave":
				case "pointerenter":
				case "pointerleave": return Hh;
				case "message": switch (Ch()) {
					case wh: return Vh;
					case Th: return Hh;
					case Eh:
					case Dh: return Uh;
					case Oh: return Wh;
					default: return Uh;
				}
				default: return Uh;
			}
		}
		function lm(e, t) {
			switch (e) {
				case "focusin":
				case "focusout":
					yE = null;
					break;
				case "dragenter":
				case "dragleave":
					bE = null;
					break;
				case "mouseover":
				case "mouseout":
					xE = null;
					break;
				case "pointerover":
				case "pointerout":
					SE.delete(t.pointerId);
					break;
				case "gotpointercapture":
				case "lostpointercapture": CE.delete(t.pointerId);
			}
		}
		function um(e, t, n, r, i, a) {
			return e === null || e.nativeEvent !== a ? (e = {
				blockedOn: t,
				domEventName: n,
				eventSystemFlags: r,
				nativeEvent: a,
				targetContainers: [i]
			}, t !== null && (t = mt(t), t !== null && em(t)), e) : (e.eventSystemFlags |= r, t = e.targetContainers, i !== null && t.indexOf(i) === -1 && t.push(i), e);
		}
		function dm(e, t, n, r, i) {
			switch (t) {
				case "focusin": return yE = um(yE, e, t, n, r, i), !0;
				case "dragenter": return bE = um(bE, e, t, n, r, i), !0;
				case "mouseover": return xE = um(xE, e, t, n, r, i), !0;
				case "pointerover":
					var a = i.pointerId;
					return SE.set(a, um(SE.get(a) || null, e, t, n, r, i)), !0;
				case "gotpointercapture": return a = i.pointerId, CE.set(a, um(CE.get(a) || null, e, t, n, r, i)), !0;
			}
			return !1;
		}
		function fm(e) {
			var t = pt(e.target);
			if (t !== null) {
				var n = ee(t);
				if (n !== null) {
					if (t = n.tag, t === 13) {
						if (t = te(n), t !== null) {
							e.blockedOn = t, dt(e.priority, function() {
								tm(n);
							});
							return;
						}
					} else if (t === 31) {
						if (t = ne(n), t !== null) {
							e.blockedOn = t, dt(e.priority, function() {
								tm(n);
							});
							return;
						}
					} else if (t === 3 && n.stateNode.current.memoizedState.isDehydrated) {
						e.blockedOn = n.tag === 3 ? n.stateNode.containerInfo : null;
						return;
					}
				}
			}
			e.blockedOn = null;
		}
		function pm(e) {
			if (e.blockedOn !== null) return !1;
			for (var t = e.targetContainers; 0 < t.length;) {
				var n = om(e.nativeEvent);
				if (n === null) {
					n = e.nativeEvent;
					var r = new n.constructor(n.type, n), i = r;
					Zg !== null && console.error("Expected currently replaying event to be null. This error is likely caused by a bug in React. Please file an issue."), Zg = i, n.target.dispatchEvent(r), Zg === null && console.error("Expected currently replaying event to not be null. This error is likely caused by a bug in React. Please file an issue."), Zg = null;
				} else return t = mt(n), t !== null && em(t), e.blockedOn = n, !1;
				t.shift();
			}
			return !0;
		}
		function mm(e, t, n) {
			pm(e) && n.delete(t);
		}
		function hm() {
			vE = !1, yE !== null && pm(yE) && (yE = null), bE !== null && pm(bE) && (bE = null), xE !== null && pm(xE) && (xE = null), SE.forEach(mm), CE.forEach(mm);
		}
		function gm(e, t) {
			e.blockedOn === t && (e.blockedOn = null, vE || (vE = !0, Cm.unstable_scheduleCallback(Cm.unstable_NormalPriority, hm)));
		}
		function _m(e) {
			EE !== e && (EE = e, Cm.unstable_scheduleCallback(Cm.unstable_NormalPriority, function() {
				EE === e && (EE = null);
				for (var t = 0; t < e.length; t += 3) {
					var n = e[t], r = e[t + 1], i = e[t + 2];
					if (typeof r != "function") {
						if (sm(r || n) === null) continue;
						break;
					}
					var a = mt(n);
					a !== null && (e.splice(t, 3), t -= 3, n = {
						pending: !0,
						data: i,
						method: n.method,
						action: r
					}, Object.freeze(n), os(a, n, r, i));
				}
			}));
		}
		function vm(e) {
			function t(t) {
				return gm(t, e);
			}
			yE !== null && gm(yE, e), bE !== null && gm(bE, e), xE !== null && gm(xE, e), SE.forEach(t), CE.forEach(t);
			for (var n = 0; n < wE.length; n++) {
				var r = wE[n];
				r.blockedOn === e && (r.blockedOn = null);
			}
			for (; 0 < wE.length && (n = wE[0], n.blockedOn === null);) fm(n), n.blockedOn === null && wE.shift();
			if (n = (e.ownerDocument || e).$$reactFormReplay, n != null) for (r = 0; r < n.length; r += 3) {
				var i = n[r], a = n[r + 1], o = i[qh] || null;
				if (typeof a == "function") o || _m(n);
				else if (o) {
					var s = null;
					if (a && a.hasAttribute("formAction")) {
						if (i = a, o = a[qh] || null) s = o.formAction;
						else if (sm(i) !== null) continue;
					} else s = o.action;
					typeof s == "function" ? n[r + 1] = s : (n.splice(r, 3), r -= 3), _m(n);
				}
			}
		}
		function ym() {
			function e(e) {
				e.canIntercept && e.info === "react-transition" && e.intercept({
					handler: function() {
						return new Promise(function(e) {
							return i = e;
						});
					},
					focusReset: "manual",
					scroll: "manual"
				});
			}
			function t() {
				i !== null && (i(), i = null), r || setTimeout(n, 20);
			}
			function n() {
				if (!r && !navigation.transition) {
					var e = navigation.currentEntry;
					e && e.url != null && navigation.navigate(e.url, {
						state: e.getState(),
						info: "react-transition",
						history: "replace"
					});
				}
			}
			if (typeof navigation == "object") {
				var r = !1, i = null;
				return navigation.addEventListener("navigate", e), navigation.addEventListener("navigatesuccess", t), navigation.addEventListener("navigateerror", t), setTimeout(n, 100), function() {
					r = !0, navigation.removeEventListener("navigate", e), navigation.removeEventListener("navigatesuccess", t), navigation.removeEventListener("navigateerror", t), i !== null && (i(), i = null);
				};
			}
		}
		function bm(e) {
			this._internalRoot = e;
		}
		function xm(e) {
			this._internalRoot = e;
		}
		function Sm(e) {
			e[Jh] && (e._reactRootContainer ? console.error("You are calling ReactDOMClient.createRoot() on a container that was previously passed to ReactDOM.render(). This is not supported.") : console.error("You are calling ReactDOMClient.createRoot() on a container that has already been passed to createRoot() before. Instead, call root.render() on the existing root instead if you want to update it."));
		}
		typeof __REACT_DEVTOOLS_GLOBAL_HOOK__ < "u" && typeof __REACT_DEVTOOLS_GLOBAL_HOOK__.registerInternalModuleStart == "function" && __REACT_DEVTOOLS_GLOBAL_HOOK__.registerInternalModuleStart(Error());
		var Cm = o(), wm = t(), Tm = l(), Em = null, Dm = null, L = Object.assign, Om = Symbol.for("react.element"), km = Symbol.for("react.transitional.element"), Am = Symbol.for("react.portal"), jm = Symbol.for("react.fragment"), Mm = Symbol.for("react.strict_mode"), Nm = Symbol.for("react.profiler"), Pm = Symbol.for("react.consumer"), Fm = Symbol.for("react.context"), Im = Symbol.for("react.forward_ref"), Lm = Symbol.for("react.suspense"), Rm = Symbol.for("react.suspense_list"), zm = Symbol.for("react.memo"), Bm = Symbol.for("react.lazy"), Vm = Symbol.for("react.activity"), Hm = Symbol.for("react.legacy_hidden"), Um = Symbol.for("react.memo_cache_sentinel"), Wm = Symbol.for("react.view_transition"), Gm = Symbol.for("react.recoverable"), Km = Symbol.iterator, qm = Symbol.for("react.client.reference"), Jm = Array.isArray, R = wm.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE, z = Tm.__DOM_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE, Ym = Object.freeze({
			pending: !1,
			data: null,
			method: null,
			action: null
		}), Xm = [], Zm = [], Qm = -1, $m = ye(null), eh = ye(null), th = ye(null), nh = ye(null), rh = 0, ih, ah, oh, sh, ch, lh, uh;
		C.__reactDisabledLog = !0;
		var dh, fh, ph = !1, mh = new (typeof WeakMap == "function" ? WeakMap : Map)(), hh = null, gh = !1, _h = Object.prototype.hasOwnProperty, vh = Cm.unstable_scheduleCallback, yh = Cm.unstable_cancelCallback, bh = Cm.unstable_shouldYield, xh = Cm.unstable_requestPaint, Sh = Cm.unstable_now, Ch = Cm.unstable_getCurrentPriorityLevel, wh = Cm.unstable_ImmediatePriority, Th = Cm.unstable_UserBlockingPriority, Eh = Cm.unstable_NormalPriority, Dh = Cm.unstable_LowPriority, Oh = Cm.unstable_IdlePriority, kh = Cm.log, Ah = Cm.unstable_setDisableYieldValue, jh = null, Mh = null, Nh = !1, Ph = typeof __REACT_DEVTOOLS_GLOBAL_HOOK__ < "u", Fh = Math.clz32 ? Math.clz32 : qe, Ih = Math.log, Lh = Math.LN2, Rh = 256, zh = 262144, Bh = 4194304, Vh = 2, Hh = 8, Uh = 32, Wh = 268435456, Gh = Math.random().toString(36).slice(2), Kh = "__reactFiber$" + Gh, qh = "__reactProps$" + Gh, Jh = "__reactContainer$" + Gh, Yh = "__reactEvents$" + Gh, Xh = "__reactListeners$" + Gh, Zh = "__reactHandles$" + Gh, Qh = "__reactResources$" + Gh, $h = "__reactMarker$" + Gh, eg = "__reactLoad$" + Gh, tg = /* @__PURE__ */ new Set(), ng = {}, rg = {}, ig = {
			button: !0,
			checkbox: !0,
			image: !0,
			hidden: !0,
			radio: !0,
			reset: !0,
			submit: !0
		}, ag = RegExp("^[:A-Z_a-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02FF\\u0370-\\u037D\\u037F-\\u1FFF\\u200C-\\u200D\\u2070-\\u218F\\u2C00-\\u2FEF\\u3001-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFFD][:A-Z_a-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02FF\\u0370-\\u037D\\u037F-\\u1FFF\\u200C-\\u200D\\u2070-\\u218F\\u2C00-\\u2FEF\\u3001-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFFD\\-.0-9\\u00B7\\u0300-\\u036F\\u203F-\\u2040]*$"), og = {}, sg = {}, B = !1, cg = /[\n"\\]/g, lg = !1, ug = !1, dg = !1, fg = !1, pg = !1, mg = !1, hg = ["value", "defaultValue"], gg = !1, _g = /["'&<>\n\t]|^\s|\s$/, vg = "address applet area article aside base basefont bgsound blockquote body br button caption center col colgroup dd details dir div dl dt embed fieldset figcaption figure footer form frame frameset h1 h2 h3 h4 h5 h6 head header hgroup hr html iframe img input isindex li link listing main marquee menu menuitem meta nav noembed noframes noscript object ol p param plaintext pre script section select source style summary table tbody td template textarea tfoot th thead title tr track ul wbr xmp".split(" "), yg = "applet caption html table td th marquee object select template foreignObject desc title".split(" "), bg = yg.concat(["button"]), xg = "dd dt li option optgroup p rp rt".split(" "), Sg = {
			current: null,
			formTag: null,
			aTagInScope: null,
			buttonTagInScope: null,
			nobrTagInScope: null,
			pTagInButtonScope: null,
			listItemTagAutoclosing: null,
			dlItemTagAutoclosing: null,
			containerTagInScope: null,
			implicitRootScope: !1
		}, Cg = {}, wg = {
			animation: "animationDelay animationDirection animationDuration animationFillMode animationIterationCount animationName animationPlayState animationTimingFunction".split(" "),
			background: "backgroundAttachment backgroundClip backgroundColor backgroundImage backgroundOrigin backgroundPositionX backgroundPositionY backgroundRepeat backgroundSize".split(" "),
			backgroundPosition: ["backgroundPositionX", "backgroundPositionY"],
			border: "borderBottomColor borderBottomStyle borderBottomWidth borderImageOutset borderImageRepeat borderImageSlice borderImageSource borderImageWidth borderLeftColor borderLeftStyle borderLeftWidth borderRightColor borderRightStyle borderRightWidth borderTopColor borderTopStyle borderTopWidth".split(" "),
			borderBlock: "borderBlockEndColor borderBlockEndStyle borderBlockEndWidth borderBlockStartColor borderBlockStartStyle borderBlockStartWidth".split(" "),
			borderBlockColor: ["borderBlockEndColor", "borderBlockStartColor"],
			borderBlockEnd: [
				"borderBlockEndColor",
				"borderBlockEndStyle",
				"borderBlockEndWidth"
			],
			borderBlockStart: [
				"borderBlockStartColor",
				"borderBlockStartStyle",
				"borderBlockStartWidth"
			],
			borderBlockStyle: ["borderBlockEndStyle", "borderBlockStartStyle"],
			borderBlockWidth: ["borderBlockEndWidth", "borderBlockStartWidth"],
			borderBottom: [
				"borderBottomColor",
				"borderBottomStyle",
				"borderBottomWidth"
			],
			borderColor: [
				"borderBottomColor",
				"borderLeftColor",
				"borderRightColor",
				"borderTopColor"
			],
			borderImage: [
				"borderImageOutset",
				"borderImageRepeat",
				"borderImageSlice",
				"borderImageSource",
				"borderImageWidth"
			],
			borderInline: "borderInlineEndColor borderInlineEndStyle borderInlineEndWidth borderInlineStartColor borderInlineStartStyle borderInlineStartWidth".split(" "),
			borderInlineColor: ["borderInlineEndColor", "borderInlineStartColor"],
			borderInlineEnd: [
				"borderInlineEndColor",
				"borderInlineEndStyle",
				"borderInlineEndWidth"
			],
			borderInlineStart: [
				"borderInlineStartColor",
				"borderInlineStartStyle",
				"borderInlineStartWidth"
			],
			borderInlineStyle: ["borderInlineEndStyle", "borderInlineStartStyle"],
			borderInlineWidth: ["borderInlineEndWidth", "borderInlineStartWidth"],
			borderLeft: [
				"borderLeftColor",
				"borderLeftStyle",
				"borderLeftWidth"
			],
			borderRadius: [
				"borderBottomLeftRadius",
				"borderBottomRightRadius",
				"borderTopLeftRadius",
				"borderTopRightRadius"
			],
			borderRight: [
				"borderRightColor",
				"borderRightStyle",
				"borderRightWidth"
			],
			borderStyle: [
				"borderBottomStyle",
				"borderLeftStyle",
				"borderRightStyle",
				"borderTopStyle"
			],
			borderTop: [
				"borderTopColor",
				"borderTopStyle",
				"borderTopWidth"
			],
			borderWidth: [
				"borderBottomWidth",
				"borderLeftWidth",
				"borderRightWidth",
				"borderTopWidth"
			],
			colorAdjust: ["printColorAdjust"],
			columnRule: [
				"columnRuleColor",
				"columnRuleStyle",
				"columnRuleWidth"
			],
			columns: ["columnCount", "columnWidth"],
			containIntrinsicSize: ["containIntrinsicHeight", "containIntrinsicWidth"],
			container: ["containerName", "containerType"],
			flex: [
				"flexBasis",
				"flexGrow",
				"flexShrink"
			],
			flexFlow: ["flexDirection", "flexWrap"],
			font: "fontFamily fontFeatureSettings fontKerning fontLanguageOverride fontSize fontSizeAdjust fontStretch fontStyle fontVariant fontVariantAlternates fontVariantCaps fontVariantEastAsian fontVariantLigatures fontVariantNumeric fontVariantPosition fontWeight lineHeight".split(" "),
			fontSynthesis: [
				"fontSynthesisPosition",
				"fontSynthesisSmallCaps",
				"fontSynthesisStyle",
				"fontSynthesisWeight"
			],
			fontVariant: "fontVariantAlternates fontVariantCaps fontVariantEastAsian fontVariantLigatures fontVariantNumeric fontVariantPosition".split(" "),
			gap: ["columnGap", "rowGap"],
			grid: "gridAutoColumns gridAutoFlow gridAutoRows gridTemplateAreas gridTemplateColumns gridTemplateRows".split(" "),
			gridArea: [
				"gridColumnEnd",
				"gridColumnStart",
				"gridRowEnd",
				"gridRowStart"
			],
			gridColumn: ["gridColumnEnd", "gridColumnStart"],
			gridColumnGap: ["columnGap"],
			gridGap: ["columnGap", "rowGap"],
			gridRow: ["gridRowEnd", "gridRowStart"],
			gridRowGap: ["rowGap"],
			gridTemplate: [
				"gridTemplateAreas",
				"gridTemplateColumns",
				"gridTemplateRows"
			],
			inset: [
				"bottom",
				"left",
				"right",
				"top"
			],
			insetBlock: ["insetBlockEnd", "insetBlockStart"],
			insetInline: ["insetInlineEnd", "insetInlineStart"],
			listStyle: [
				"listStyleImage",
				"listStylePosition",
				"listStyleType"
			],
			margin: [
				"marginBottom",
				"marginLeft",
				"marginRight",
				"marginTop"
			],
			marginBlock: ["marginBlockEnd", "marginBlockStart"],
			marginInline: ["marginInlineEnd", "marginInlineStart"],
			marker: [
				"markerEnd",
				"markerMid",
				"markerStart"
			],
			mask: "maskClip maskComposite maskImage maskMode maskOrigin maskPositionX maskPositionY maskRepeat maskSize".split(" "),
			maskPosition: ["maskPositionX", "maskPositionY"],
			offset: [
				"offsetAnchor",
				"offsetDistance",
				"offsetPath",
				"offsetPosition",
				"offsetRotate"
			],
			outline: [
				"outlineColor",
				"outlineStyle",
				"outlineWidth"
			],
			overflow: ["overflowX", "overflowY"],
			overscrollBehavior: ["overscrollBehaviorX", "overscrollBehaviorY"],
			padding: [
				"paddingBottom",
				"paddingLeft",
				"paddingRight",
				"paddingTop"
			],
			paddingBlock: ["paddingBlockEnd", "paddingBlockStart"],
			paddingInline: ["paddingInlineEnd", "paddingInlineStart"],
			pageBreakAfter: ["breakAfter"],
			pageBreakBefore: ["breakBefore"],
			pageBreakInside: ["breakInside"],
			placeContent: ["alignContent", "justifyContent"],
			placeItems: ["alignItems", "justifyItems"],
			placeSelf: ["alignSelf", "justifySelf"],
			scrollMargin: [
				"scrollMarginBottom",
				"scrollMarginLeft",
				"scrollMarginRight",
				"scrollMarginTop"
			],
			scrollMarginBlock: ["scrollMarginBlockEnd", "scrollMarginBlockStart"],
			scrollMarginInline: ["scrollMarginInlineEnd", "scrollMarginInlineStart"],
			scrollPadding: [
				"scrollPaddingBottom",
				"scrollPaddingLeft",
				"scrollPaddingRight",
				"scrollPaddingTop"
			],
			scrollPaddingBlock: ["scrollPaddingBlockEnd", "scrollPaddingBlockStart"],
			scrollPaddingInline: ["scrollPaddingInlineEnd", "scrollPaddingInlineStart"],
			textDecoration: [
				"textDecorationColor",
				"textDecorationLine",
				"textDecorationStyle",
				"textDecorationThickness"
			],
			textEmphasis: ["textEmphasisColor", "textEmphasisStyle"],
			textWrap: ["textWrapMode", "textWrapStyle"],
			transition: [
				"transitionBehavior",
				"transitionDelay",
				"transitionDuration",
				"transitionProperty",
				"transitionTimingFunction"
			],
			verticalAlign: [
				"alignmentBaseline",
				"baselineShift",
				"baselineSource"
			],
			whiteSpace: ["textWrapMode", "whiteSpaceCollapse"],
			wordWrap: ["overflowWrap"]
		}, Tg = /([A-Z])/g, Eg = /^ms-/, Dg = /^(?:webkit|moz|o)[A-Z]/, Og = /^-ms-/, kg = /-(.)/g, Ag = /;\s*$/, jg = {}, Mg = {}, Ng = !1, Pg = !1, Fg = new Set("animationIterationCount aspectRatio borderImageOutset borderImageSlice borderImageWidth boxFlex boxFlexGroup boxOrdinalGroup columnCount columns flex flexGrow flexPositive flexShrink flexNegative flexOrder gridArea gridRow gridRowEnd gridRowSpan gridRowStart gridColumn gridColumnEnd gridColumnSpan gridColumnStart fontWeight lineClamp lineHeight opacity order orphans scale tabSize widows zIndex zoom fillOpacity floodOpacity stopOpacity strokeDasharray strokeDashoffset strokeMiterlimit strokeOpacity strokeWidth MozAnimationIterationCount MozBoxFlex MozBoxFlexGroup MozLineClamp msAnimationIterationCount msFlex msZoom msFlexGrow msFlexNegative msFlexOrder msFlexPositive msFlexShrink msGridColumn msGridColumnSpan msGridRow msGridRowSpan WebkitAnimationIterationCount WebkitBoxFlex WebKitBoxFlexGroup WebkitBoxOrdinalGroup WebkitColumnCount WebkitColumns WebkitFlex WebkitFlexGrow WebkitFlexPositive WebkitFlexShrink WebkitLineClamp".split(" ")), Ig = "http://www.w3.org/1998/Math/MathML", Lg = "http://www.w3.org/2000/svg", Rg = /* @__PURE__ */ new Map([
			["acceptCharset", "accept-charset"],
			["htmlFor", "for"],
			["httpEquiv", "http-equiv"],
			["crossOrigin", "crossorigin"],
			["accentHeight", "accent-height"],
			["alignmentBaseline", "alignment-baseline"],
			["arabicForm", "arabic-form"],
			["baselineShift", "baseline-shift"],
			["capHeight", "cap-height"],
			["clipPath", "clip-path"],
			["clipRule", "clip-rule"],
			["colorInterpolation", "color-interpolation"],
			["colorInterpolationFilters", "color-interpolation-filters"],
			["colorProfile", "color-profile"],
			["colorRendering", "color-rendering"],
			["dominantBaseline", "dominant-baseline"],
			["enableBackground", "enable-background"],
			["fillOpacity", "fill-opacity"],
			["fillRule", "fill-rule"],
			["floodColor", "flood-color"],
			["floodOpacity", "flood-opacity"],
			["fontFamily", "font-family"],
			["fontSize", "font-size"],
			["fontSizeAdjust", "font-size-adjust"],
			["fontStretch", "font-stretch"],
			["fontStyle", "font-style"],
			["fontVariant", "font-variant"],
			["fontWeight", "font-weight"],
			["glyphName", "glyph-name"],
			["glyphOrientationHorizontal", "glyph-orientation-horizontal"],
			["glyphOrientationVertical", "glyph-orientation-vertical"],
			["horizAdvX", "horiz-adv-x"],
			["horizOriginX", "horiz-origin-x"],
			["imageRendering", "image-rendering"],
			["letterSpacing", "letter-spacing"],
			["lightingColor", "lighting-color"],
			["markerEnd", "marker-end"],
			["markerMid", "marker-mid"],
			["markerStart", "marker-start"],
			["maskType", "mask-type"],
			["overlinePosition", "overline-position"],
			["overlineThickness", "overline-thickness"],
			["paintOrder", "paint-order"],
			["panose-1", "panose-1"],
			["pointerEvents", "pointer-events"],
			["renderingIntent", "rendering-intent"],
			["shapeRendering", "shape-rendering"],
			["stopColor", "stop-color"],
			["stopOpacity", "stop-opacity"],
			["strikethroughPosition", "strikethrough-position"],
			["strikethroughThickness", "strikethrough-thickness"],
			["strokeDasharray", "stroke-dasharray"],
			["strokeDashoffset", "stroke-dashoffset"],
			["strokeLinecap", "stroke-linecap"],
			["strokeLinejoin", "stroke-linejoin"],
			["strokeMiterlimit", "stroke-miterlimit"],
			["strokeOpacity", "stroke-opacity"],
			["strokeWidth", "stroke-width"],
			["textAnchor", "text-anchor"],
			["textDecoration", "text-decoration"],
			["textRendering", "text-rendering"],
			["transformOrigin", "transform-origin"],
			["underlinePosition", "underline-position"],
			["underlineThickness", "underline-thickness"],
			["unicodeBidi", "unicode-bidi"],
			["unicodeRange", "unicode-range"],
			["unitsPerEm", "units-per-em"],
			["vAlphabetic", "v-alphabetic"],
			["vHanging", "v-hanging"],
			["vIdeographic", "v-ideographic"],
			["vMathematical", "v-mathematical"],
			["vectorEffect", "vector-effect"],
			["vertAdvY", "vert-adv-y"],
			["vertOriginX", "vert-origin-x"],
			["vertOriginY", "vert-origin-y"],
			["wordSpacing", "word-spacing"],
			["writingMode", "writing-mode"],
			["xmlnsXlink", "xmlns:xlink"],
			["xHeight", "x-height"]
		]), zg = {
			accept: "accept",
			acceptcharset: "acceptCharset",
			"accept-charset": "acceptCharset",
			accesskey: "accessKey",
			action: "action",
			allowfullscreen: "allowFullScreen",
			alt: "alt",
			as: "as",
			async: "async",
			autocapitalize: "autoCapitalize",
			autocomplete: "autoComplete",
			autocorrect: "autoCorrect",
			autofocus: "autoFocus",
			autoplay: "autoPlay",
			autosave: "autoSave",
			capture: "capture",
			cellpadding: "cellPadding",
			cellspacing: "cellSpacing",
			challenge: "challenge",
			charset: "charSet",
			checked: "checked",
			children: "children",
			cite: "cite",
			class: "className",
			classid: "classID",
			classname: "className",
			cols: "cols",
			colspan: "colSpan",
			content: "content",
			contenteditable: "contentEditable",
			contextmenu: "contextMenu",
			controls: "controls",
			controlslist: "controlsList",
			coords: "coords",
			credentialless: "credentialless",
			crossorigin: "crossOrigin",
			dangerouslysetinnerhtml: "dangerouslySetInnerHTML",
			data: "data",
			datetime: "dateTime",
			default: "default",
			defaultchecked: "defaultChecked",
			defaultvalue: "defaultValue",
			defer: "defer",
			dir: "dir",
			disabled: "disabled",
			disablepictureinpicture: "disablePictureInPicture",
			disableremoteplayback: "disableRemotePlayback",
			download: "download",
			draggable: "draggable",
			enctype: "encType",
			enterkeyhint: "enterKeyHint",
			fetchpriority: "fetchPriority",
			for: "htmlFor",
			form: "form",
			formmethod: "formMethod",
			formaction: "formAction",
			formenctype: "formEncType",
			formnovalidate: "formNoValidate",
			formtarget: "formTarget",
			frameborder: "frameBorder",
			headers: "headers",
			height: "height",
			hidden: "hidden",
			high: "high",
			href: "href",
			hreflang: "hrefLang",
			htmlfor: "htmlFor",
			httpequiv: "httpEquiv",
			"http-equiv": "httpEquiv",
			icon: "icon",
			id: "id",
			imagesizes: "imageSizes",
			imagesrcset: "imageSrcSet",
			inert: "inert",
			innerhtml: "innerHTML",
			inputmode: "inputMode",
			integrity: "integrity",
			is: "is",
			itemid: "itemID",
			itemprop: "itemProp",
			itemref: "itemRef",
			itemscope: "itemScope",
			itemtype: "itemType",
			keyparams: "keyParams",
			keytype: "keyType",
			kind: "kind",
			label: "label",
			lang: "lang",
			list: "list",
			loop: "loop",
			low: "low",
			manifest: "manifest",
			marginwidth: "marginWidth",
			marginheight: "marginHeight",
			max: "max",
			maxlength: "maxLength",
			media: "media",
			mediagroup: "mediaGroup",
			method: "method",
			min: "min",
			minlength: "minLength",
			multiple: "multiple",
			muted: "muted",
			name: "name",
			nomodule: "noModule",
			nonce: "nonce",
			novalidate: "noValidate",
			open: "open",
			optimum: "optimum",
			pattern: "pattern",
			placeholder: "placeholder",
			playsinline: "playsInline",
			poster: "poster",
			preload: "preload",
			profile: "profile",
			radiogroup: "radioGroup",
			readonly: "readOnly",
			referrerpolicy: "referrerPolicy",
			rel: "rel",
			required: "required",
			reversed: "reversed",
			role: "role",
			rows: "rows",
			rowspan: "rowSpan",
			sandbox: "sandbox",
			scope: "scope",
			scoped: "scoped",
			scrolling: "scrolling",
			seamless: "seamless",
			selected: "selected",
			shape: "shape",
			size: "size",
			sizes: "sizes",
			span: "span",
			spellcheck: "spellCheck",
			src: "src",
			srcdoc: "srcDoc",
			srclang: "srcLang",
			srcset: "srcSet",
			start: "start",
			step: "step",
			style: "style",
			summary: "summary",
			tabindex: "tabIndex",
			target: "target",
			title: "title",
			type: "type",
			usemap: "useMap",
			value: "value",
			width: "width",
			wmode: "wmode",
			wrap: "wrap",
			about: "about",
			accentheight: "accentHeight",
			"accent-height": "accentHeight",
			accumulate: "accumulate",
			additive: "additive",
			alignmentbaseline: "alignmentBaseline",
			"alignment-baseline": "alignmentBaseline",
			allowreorder: "allowReorder",
			alphabetic: "alphabetic",
			amplitude: "amplitude",
			arabicform: "arabicForm",
			"arabic-form": "arabicForm",
			ascent: "ascent",
			attributename: "attributeName",
			attributetype: "attributeType",
			autoreverse: "autoReverse",
			azimuth: "azimuth",
			basefrequency: "baseFrequency",
			baselineshift: "baselineShift",
			"baseline-shift": "baselineShift",
			baseprofile: "baseProfile",
			bbox: "bbox",
			begin: "begin",
			bias: "bias",
			by: "by",
			calcmode: "calcMode",
			capheight: "capHeight",
			"cap-height": "capHeight",
			clip: "clip",
			clippath: "clipPath",
			"clip-path": "clipPath",
			clippathunits: "clipPathUnits",
			cliprule: "clipRule",
			"clip-rule": "clipRule",
			color: "color",
			colorinterpolation: "colorInterpolation",
			"color-interpolation": "colorInterpolation",
			colorinterpolationfilters: "colorInterpolationFilters",
			"color-interpolation-filters": "colorInterpolationFilters",
			colorprofile: "colorProfile",
			"color-profile": "colorProfile",
			colorrendering: "colorRendering",
			"color-rendering": "colorRendering",
			contentscripttype: "contentScriptType",
			contentstyletype: "contentStyleType",
			cursor: "cursor",
			cx: "cx",
			cy: "cy",
			d: "d",
			datatype: "datatype",
			decelerate: "decelerate",
			descent: "descent",
			diffuseconstant: "diffuseConstant",
			direction: "direction",
			display: "display",
			divisor: "divisor",
			dominantbaseline: "dominantBaseline",
			"dominant-baseline": "dominantBaseline",
			dur: "dur",
			dx: "dx",
			dy: "dy",
			edgemode: "edgeMode",
			elevation: "elevation",
			enablebackground: "enableBackground",
			"enable-background": "enableBackground",
			end: "end",
			exponent: "exponent",
			externalresourcesrequired: "externalResourcesRequired",
			fill: "fill",
			fillopacity: "fillOpacity",
			"fill-opacity": "fillOpacity",
			fillrule: "fillRule",
			"fill-rule": "fillRule",
			filter: "filter",
			filterres: "filterRes",
			filterunits: "filterUnits",
			floodopacity: "floodOpacity",
			"flood-opacity": "floodOpacity",
			floodcolor: "floodColor",
			"flood-color": "floodColor",
			focusable: "focusable",
			fontfamily: "fontFamily",
			"font-family": "fontFamily",
			fontsize: "fontSize",
			"font-size": "fontSize",
			fontsizeadjust: "fontSizeAdjust",
			"font-size-adjust": "fontSizeAdjust",
			fontstretch: "fontStretch",
			"font-stretch": "fontStretch",
			fontstyle: "fontStyle",
			"font-style": "fontStyle",
			fontvariant: "fontVariant",
			"font-variant": "fontVariant",
			fontweight: "fontWeight",
			"font-weight": "fontWeight",
			format: "format",
			from: "from",
			fx: "fx",
			fy: "fy",
			g1: "g1",
			g2: "g2",
			glyphname: "glyphName",
			"glyph-name": "glyphName",
			glyphorientationhorizontal: "glyphOrientationHorizontal",
			"glyph-orientation-horizontal": "glyphOrientationHorizontal",
			glyphorientationvertical: "glyphOrientationVertical",
			"glyph-orientation-vertical": "glyphOrientationVertical",
			glyphref: "glyphRef",
			gradienttransform: "gradientTransform",
			gradientunits: "gradientUnits",
			hanging: "hanging",
			horizadvx: "horizAdvX",
			"horiz-adv-x": "horizAdvX",
			horizoriginx: "horizOriginX",
			"horiz-origin-x": "horizOriginX",
			ideographic: "ideographic",
			imagerendering: "imageRendering",
			"image-rendering": "imageRendering",
			in2: "in2",
			in: "in",
			inlist: "inlist",
			intercept: "intercept",
			k1: "k1",
			k2: "k2",
			k3: "k3",
			k4: "k4",
			k: "k",
			kernelmatrix: "kernelMatrix",
			kernelunitlength: "kernelUnitLength",
			kerning: "kerning",
			keypoints: "keyPoints",
			keysplines: "keySplines",
			keytimes: "keyTimes",
			lengthadjust: "lengthAdjust",
			letterspacing: "letterSpacing",
			"letter-spacing": "letterSpacing",
			lightingcolor: "lightingColor",
			"lighting-color": "lightingColor",
			limitingconeangle: "limitingConeAngle",
			local: "local",
			markerend: "markerEnd",
			"marker-end": "markerEnd",
			markerheight: "markerHeight",
			markermid: "markerMid",
			"marker-mid": "markerMid",
			markerstart: "markerStart",
			"marker-start": "markerStart",
			markerunits: "markerUnits",
			markerwidth: "markerWidth",
			mask: "mask",
			maskcontentunits: "maskContentUnits",
			masktype: "maskType",
			maskunits: "maskUnits",
			mathematical: "mathematical",
			mode: "mode",
			numoctaves: "numOctaves",
			offset: "offset",
			opacity: "opacity",
			operator: "operator",
			order: "order",
			orient: "orient",
			orientation: "orientation",
			origin: "origin",
			overflow: "overflow",
			overlineposition: "overlinePosition",
			"overline-position": "overlinePosition",
			overlinethickness: "overlineThickness",
			"overline-thickness": "overlineThickness",
			paintorder: "paintOrder",
			"paint-order": "paintOrder",
			panose1: "panose1",
			"panose-1": "panose1",
			pathlength: "pathLength",
			patterncontentunits: "patternContentUnits",
			patterntransform: "patternTransform",
			patternunits: "patternUnits",
			pointerevents: "pointerEvents",
			"pointer-events": "pointerEvents",
			points: "points",
			pointsatx: "pointsAtX",
			pointsaty: "pointsAtY",
			pointsatz: "pointsAtZ",
			popover: "popover",
			popovertarget: "popoverTarget",
			popovertargetaction: "popoverTargetAction",
			prefix: "prefix",
			preservealpha: "preserveAlpha",
			preserveaspectratio: "preserveAspectRatio",
			primitiveunits: "primitiveUnits",
			property: "property",
			r: "r",
			radius: "radius",
			refx: "refX",
			refy: "refY",
			renderingintent: "renderingIntent",
			"rendering-intent": "renderingIntent",
			repeatcount: "repeatCount",
			repeatdur: "repeatDur",
			requiredextensions: "requiredExtensions",
			requiredfeatures: "requiredFeatures",
			resource: "resource",
			restart: "restart",
			result: "result",
			results: "results",
			rotate: "rotate",
			rx: "rx",
			ry: "ry",
			scale: "scale",
			security: "security",
			seed: "seed",
			shaperendering: "shapeRendering",
			"shape-rendering": "shapeRendering",
			slope: "slope",
			spacing: "spacing",
			specularconstant: "specularConstant",
			specularexponent: "specularExponent",
			speed: "speed",
			spreadmethod: "spreadMethod",
			startoffset: "startOffset",
			stddeviation: "stdDeviation",
			stemh: "stemh",
			stemv: "stemv",
			stitchtiles: "stitchTiles",
			stopcolor: "stopColor",
			"stop-color": "stopColor",
			stopopacity: "stopOpacity",
			"stop-opacity": "stopOpacity",
			strikethroughposition: "strikethroughPosition",
			"strikethrough-position": "strikethroughPosition",
			strikethroughthickness: "strikethroughThickness",
			"strikethrough-thickness": "strikethroughThickness",
			string: "string",
			stroke: "stroke",
			strokedasharray: "strokeDasharray",
			"stroke-dasharray": "strokeDasharray",
			strokedashoffset: "strokeDashoffset",
			"stroke-dashoffset": "strokeDashoffset",
			strokelinecap: "strokeLinecap",
			"stroke-linecap": "strokeLinecap",
			strokelinejoin: "strokeLinejoin",
			"stroke-linejoin": "strokeLinejoin",
			strokemiterlimit: "strokeMiterlimit",
			"stroke-miterlimit": "strokeMiterlimit",
			strokewidth: "strokeWidth",
			"stroke-width": "strokeWidth",
			strokeopacity: "strokeOpacity",
			"stroke-opacity": "strokeOpacity",
			suppresscontenteditablewarning: "suppressContentEditableWarning",
			suppresshydrationwarning: "suppressHydrationWarning",
			surfacescale: "surfaceScale",
			systemlanguage: "systemLanguage",
			tablevalues: "tableValues",
			targetx: "targetX",
			targety: "targetY",
			textanchor: "textAnchor",
			"text-anchor": "textAnchor",
			textdecoration: "textDecoration",
			"text-decoration": "textDecoration",
			textlength: "textLength",
			textrendering: "textRendering",
			"text-rendering": "textRendering",
			to: "to",
			transform: "transform",
			transformorigin: "transformOrigin",
			"transform-origin": "transformOrigin",
			typeof: "typeof",
			u1: "u1",
			u2: "u2",
			underlineposition: "underlinePosition",
			"underline-position": "underlinePosition",
			underlinethickness: "underlineThickness",
			"underline-thickness": "underlineThickness",
			unicode: "unicode",
			unicodebidi: "unicodeBidi",
			"unicode-bidi": "unicodeBidi",
			unicoderange: "unicodeRange",
			"unicode-range": "unicodeRange",
			unitsperem: "unitsPerEm",
			"units-per-em": "unitsPerEm",
			unselectable: "unselectable",
			valphabetic: "vAlphabetic",
			"v-alphabetic": "vAlphabetic",
			values: "values",
			vectoreffect: "vectorEffect",
			"vector-effect": "vectorEffect",
			version: "version",
			vertadvy: "vertAdvY",
			"vert-adv-y": "vertAdvY",
			vertoriginx: "vertOriginX",
			"vert-origin-x": "vertOriginX",
			vertoriginy: "vertOriginY",
			"vert-origin-y": "vertOriginY",
			vhanging: "vHanging",
			"v-hanging": "vHanging",
			videographic: "vIdeographic",
			"v-ideographic": "vIdeographic",
			viewbox: "viewBox",
			viewtarget: "viewTarget",
			visibility: "visibility",
			vmathematical: "vMathematical",
			"v-mathematical": "vMathematical",
			vocab: "vocab",
			widths: "widths",
			wordspacing: "wordSpacing",
			"word-spacing": "wordSpacing",
			writingmode: "writingMode",
			"writing-mode": "writingMode",
			x1: "x1",
			x2: "x2",
			x: "x",
			xchannelselector: "xChannelSelector",
			xheight: "xHeight",
			"x-height": "xHeight",
			xlinkactuate: "xlinkActuate",
			"xlink:actuate": "xlinkActuate",
			xlinkarcrole: "xlinkArcrole",
			"xlink:arcrole": "xlinkArcrole",
			xlinkhref: "xlinkHref",
			"xlink:href": "xlinkHref",
			xlinkrole: "xlinkRole",
			"xlink:role": "xlinkRole",
			xlinkshow: "xlinkShow",
			"xlink:show": "xlinkShow",
			xlinktitle: "xlinkTitle",
			"xlink:title": "xlinkTitle",
			xlinktype: "xlinkType",
			"xlink:type": "xlinkType",
			xmlbase: "xmlBase",
			"xml:base": "xmlBase",
			xmllang: "xmlLang",
			"xml:lang": "xmlLang",
			xmlns: "xmlns",
			"xml:space": "xmlSpace",
			xmlnsxlink: "xmlnsXlink",
			"xmlns:xlink": "xmlnsXlink",
			xmlspace: "xmlSpace",
			y1: "y1",
			y2: "y2",
			y: "y",
			ychannelselector: "yChannelSelector",
			z: "z",
			zoomandpan: "zoomAndPan"
		}, Bg = {
			"aria-current": 0,
			"aria-description": 0,
			"aria-details": 0,
			"aria-disabled": 0,
			"aria-hidden": 0,
			"aria-invalid": 0,
			"aria-keyshortcuts": 0,
			"aria-label": 0,
			"aria-roledescription": 0,
			"aria-autocomplete": 0,
			"aria-checked": 0,
			"aria-expanded": 0,
			"aria-haspopup": 0,
			"aria-level": 0,
			"aria-modal": 0,
			"aria-multiline": 0,
			"aria-multiselectable": 0,
			"aria-orientation": 0,
			"aria-placeholder": 0,
			"aria-pressed": 0,
			"aria-readonly": 0,
			"aria-required": 0,
			"aria-selected": 0,
			"aria-sort": 0,
			"aria-valuemax": 0,
			"aria-valuemin": 0,
			"aria-valuenow": 0,
			"aria-valuetext": 0,
			"aria-atomic": 0,
			"aria-busy": 0,
			"aria-live": 0,
			"aria-relevant": 0,
			"aria-dropeffect": 0,
			"aria-grabbed": 0,
			"aria-activedescendant": 0,
			"aria-colcount": 0,
			"aria-colindex": 0,
			"aria-colspan": 0,
			"aria-controls": 0,
			"aria-describedby": 0,
			"aria-errormessage": 0,
			"aria-flowto": 0,
			"aria-labelledby": 0,
			"aria-owns": 0,
			"aria-posinset": 0,
			"aria-rowcount": 0,
			"aria-rowindex": 0,
			"aria-rowspan": 0,
			"aria-setsize": 0,
			"aria-braillelabel": 0,
			"aria-brailleroledescription": 0,
			"aria-colindextext": 0,
			"aria-rowindextext": 0
		}, Vg = {}, Hg = RegExp("^(aria)-[:A-Z_a-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02FF\\u0370-\\u037D\\u037F-\\u1FFF\\u200C-\\u200D\\u2070-\\u218F\\u2C00-\\u2FEF\\u3001-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFFD\\-.0-9\\u00B7\\u0300-\\u036F\\u203F-\\u2040]*$"), Ug = RegExp("^(aria)[A-Z][:A-Z_a-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02FF\\u0370-\\u037D\\u037F-\\u1FFF\\u200C-\\u200D\\u2070-\\u218F\\u2C00-\\u2FEF\\u3001-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFFD\\-.0-9\\u00B7\\u0300-\\u036F\\u203F-\\u2040]*$"), Wg = !1, Gg = {}, Kg = /^on./, qg = /^on[^A-Z]/, Jg = RegExp("^(aria)-[:A-Z_a-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02FF\\u0370-\\u037D\\u037F-\\u1FFF\\u200C-\\u200D\\u2070-\\u218F\\u2C00-\\u2FEF\\u3001-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFFD\\-.0-9\\u00B7\\u0300-\\u036F\\u203F-\\u2040]*$"), Yg = RegExp("^(aria)[A-Z][:A-Z_a-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02FF\\u0370-\\u037D\\u037F-\\u1FFF\\u200C-\\u200D\\u2070-\\u218F\\u2C00-\\u2FEF\\u3001-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFFD\\-.0-9\\u00B7\\u0300-\\u036F\\u203F-\\u2040]*$"), Xg = /^[\u0000-\u001F ]*j[\r\n\t]*a[\r\n\t]*v[\r\n\t]*a[\r\n\t]*s[\r\n\t]*c[\r\n\t]*r[\r\n\t]*i[\r\n\t]*p[\r\n\t]*t[\r\n\t]*:/i, Zg = null, Qg = null, $g = null, e_ = !1, t_ = typeof window < "u" && window.document !== void 0 && window.document.createElement !== void 0, n_ = !1;
		if (t_) try {
			var r_ = {};
			Object.defineProperty(r_, "passive", { get: function() {
				n_ = !0;
			} }), window.addEventListener("test", r_, r_), window.removeEventListener("test", r_, r_);
		} catch {
			n_ = !1;
		}
		var i_ = null, a_ = null, o_ = null, s_ = {
			eventPhase: 0,
			bubbles: 0,
			cancelable: 0,
			timeStamp: function(e) {
				return e.timeStamp || Date.now();
			},
			defaultPrevented: 0,
			isTrusted: 0
		}, c_ = Pn(s_), l_ = L({}, s_, {
			view: 0,
			detail: 0
		}), u_ = Pn(l_), d_, f_, p_, m_ = L({}, l_, {
			screenX: 0,
			screenY: 0,
			clientX: 0,
			clientY: 0,
			pageX: 0,
			pageY: 0,
			ctrlKey: 0,
			shiftKey: 0,
			altKey: 0,
			metaKey: 0,
			getModifierState: In,
			button: 0,
			buttons: 0,
			relatedTarget: function(e) {
				return e.relatedTarget === void 0 ? e.fromElement === e.srcElement ? e.toElement : e.fromElement : e.relatedTarget;
			},
			movementX: function(e) {
				return "movementX" in e ? e.movementX : (e !== p_ && (p_ && e.type === "mousemove" ? (d_ = e.screenX - p_.screenX, f_ = e.screenY - p_.screenY) : f_ = d_ = 0, p_ = e), d_);
			},
			movementY: function(e) {
				return "movementY" in e ? e.movementY : f_;
			}
		}), h_ = Pn(m_), g_ = Pn(L({}, m_, { dataTransfer: 0 })), __ = Pn(L({}, l_, { relatedTarget: 0 })), v_ = Pn(L({}, s_, {
			animationName: 0,
			elapsedTime: 0,
			pseudoElement: 0
		})), y_ = Pn(L({}, s_, { clipboardData: function(e) {
			return "clipboardData" in e ? e.clipboardData : window.clipboardData;
		} })), b_ = Pn(L({}, s_, { data: 0 })), x_ = b_, S_ = {
			Esc: "Escape",
			Spacebar: " ",
			Left: "ArrowLeft",
			Up: "ArrowUp",
			Right: "ArrowRight",
			Down: "ArrowDown",
			Del: "Delete",
			Win: "OS",
			Menu: "ContextMenu",
			Apps: "ContextMenu",
			Scroll: "ScrollLock",
			MozPrintableKey: "Unidentified"
		}, C_ = {
			8: "Backspace",
			9: "Tab",
			12: "Clear",
			13: "Enter",
			16: "Shift",
			17: "Control",
			18: "Alt",
			19: "Pause",
			20: "CapsLock",
			27: "Escape",
			32: " ",
			33: "PageUp",
			34: "PageDown",
			35: "End",
			36: "Home",
			37: "ArrowLeft",
			38: "ArrowUp",
			39: "ArrowRight",
			40: "ArrowDown",
			45: "Insert",
			46: "Delete",
			112: "F1",
			113: "F2",
			114: "F3",
			115: "F4",
			116: "F5",
			117: "F6",
			118: "F7",
			119: "F8",
			120: "F9",
			121: "F10",
			122: "F11",
			123: "F12",
			144: "NumLock",
			145: "ScrollLock",
			224: "Meta"
		}, w_ = {
			Alt: "altKey",
			Control: "ctrlKey",
			Meta: "metaKey",
			Shift: "shiftKey"
		}, T_ = Pn(L({}, l_, {
			key: function(e) {
				if (e.key) {
					var t = S_[e.key] || e.key;
					if (t !== "Unidentified") return t;
				}
				return e.type === "keypress" ? (e = jn(e), e === 13 ? "Enter" : String.fromCharCode(e)) : e.type === "keydown" || e.type === "keyup" ? C_[e.keyCode] || "Unidentified" : "";
			},
			code: 0,
			location: 0,
			ctrlKey: 0,
			shiftKey: 0,
			altKey: 0,
			metaKey: 0,
			repeat: 0,
			locale: 0,
			getModifierState: In,
			charCode: function(e) {
				return e.type === "keypress" ? jn(e) : 0;
			},
			keyCode: function(e) {
				return e.type === "keydown" || e.type === "keyup" ? e.keyCode : 0;
			},
			which: function(e) {
				return e.type === "keypress" ? jn(e) : e.type === "keydown" || e.type === "keyup" ? e.keyCode : 0;
			}
		})), E_ = Pn(L({}, m_, {
			pointerId: 0,
			width: 0,
			height: 0,
			pressure: 0,
			tangentialPressure: 0,
			tiltX: 0,
			tiltY: 0,
			twist: 0,
			pointerType: 0,
			isPrimary: 0
		})), D_ = Pn(L({}, s_, { submitter: 0 })), O_ = Pn(L({}, l_, {
			touches: 0,
			targetTouches: 0,
			changedTouches: 0,
			altKey: 0,
			metaKey: 0,
			ctrlKey: 0,
			shiftKey: 0,
			getModifierState: In
		})), k_ = Pn(L({}, s_, {
			propertyName: 0,
			elapsedTime: 0,
			pseudoElement: 0
		})), A_ = Pn(L({}, m_, {
			deltaX: function(e) {
				return "deltaX" in e ? e.deltaX : "wheelDeltaX" in e ? -e.wheelDeltaX : 0;
			},
			deltaY: function(e) {
				return "deltaY" in e ? e.deltaY : "wheelDeltaY" in e ? -e.wheelDeltaY : "wheelDelta" in e ? -e.wheelDelta : 0;
			},
			deltaZ: 0,
			deltaMode: 0
		})), j_ = Pn(L({}, s_, {
			newState: 0,
			oldState: 0,
			source: 0
		})), M_ = [
			9,
			13,
			27,
			32
		], N_ = 229, P_ = t_ && "CompositionEvent" in window, F_ = null;
		t_ && "documentMode" in document && (F_ = document.documentMode);
		var I_ = t_ && "TextEvent" in window && !F_, L_ = t_ && (!P_ || F_ && 8 < F_ && 11 >= F_), R_ = 32, z_ = String.fromCharCode(R_), B_ = !1, V_ = !1, H_ = {
			color: !0,
			date: !0,
			datetime: !0,
			"datetime-local": !0,
			email: !0,
			month: !0,
			number: !0,
			password: !0,
			range: !0,
			search: !0,
			tel: !0,
			text: !0,
			time: !0,
			url: !0,
			week: !0
		}, U_ = null, W_ = null, G_ = !1;
		t_ && (G_ = Hn("input") && (!document.documentMode || 9 < document.documentMode));
		var K_ = typeof Object.is == "function" ? Object.is : $n, q_ = t_ && "documentMode" in document && 11 >= document.documentMode, J_ = null, Y_ = null, X_ = null, Z_ = !1, Q_ = {
			animationend: cr("Animation", "AnimationEnd"),
			animationiteration: cr("Animation", "AnimationIteration"),
			animationstart: cr("Animation", "AnimationStart"),
			transitionrun: cr("Transition", "TransitionRun"),
			transitionstart: cr("Transition", "TransitionStart"),
			transitioncancel: cr("Transition", "TransitionCancel"),
			transitionend: cr("Transition", "TransitionEnd")
		}, $_ = {}, ev = {};
		t_ && (ev = document.createElement("div").style, "AnimationEvent" in window || (delete Q_.animationend.animation, delete Q_.animationiteration.animation, delete Q_.animationstart.animation), "TransitionEvent" in window || delete Q_.transitionend.transition);
		var tv = lr("animationend"), nv = lr("animationiteration"), rv = lr("animationstart"), iv = lr("transitionrun"), av = lr("transitionstart"), ov = lr("transitioncancel"), sv = lr("transitionend"), cv = /* @__PURE__ */ new Map(), lv = "abort auxClick beforeToggle cancel canPlay canPlayThrough click close contextMenu copy cut drag dragEnd dragEnter dragExit dragLeave dragOver dragStart drop durationChange emptied encrypted ended error fullscreenChange fullscreenError gotPointerCapture input invalid keyDown keyPress keyUp load loadedData loadedMetadata loadStart lostPointerCapture mouseDown mouseMove mouseOut mouseOver mouseUp paste pause play playing pointerCancel pointerDown pointerMove pointerOut pointerOver pointerUp progress rateChange reset resize seeked seeking stalled submit suspend timeUpdate touchCancel touchEnd touchStart volumeChange scroll toggle touchMove waiting wheel".split(" ");
		lv.push("scrollEnd");
		var uv = 0, dv = 0;
		if (typeof performance == "object" && typeof performance.now == "function") var fv = performance, pv = function() {
			return fv.now();
		};
		else {
			var mv = Date;
			pv = function() {
				return mv.now();
			};
		}
		var hv = typeof reportError == "function" ? reportError : function(e) {
			if (typeof window == "object" && typeof window.ErrorEvent == "function") {
				var t = new window.ErrorEvent("error", {
					bubbles: !0,
					cancelable: !0,
					message: typeof e == "object" && e && typeof e.message == "string" ? String(e.message) : String(e),
					error: e
				});
				if (!window.dispatchEvent(t)) return;
			} else if (typeof process == "object" && typeof process.emit == "function") {
				process.emit("uncaughtException", e);
				return;
			}
			console.error(e);
		}, gv = "This object has been omitted by React in the console log to avoid sending too much data from the server. Try logging smaller or more specific objects.", _v = 0, vv = 1, yv = 2, bv = 3, xv = 100, Sv = "-\xA0", Cv = "+\xA0", wv = " \xA0", Tv = typeof console < "u" && typeof console.timeStamp == "function" && typeof performance < "u" && typeof performance.measure == "function", Ev = "Components ⚛", V = "Scheduler ⚛", H = "Blocking", Dv = !1, Ov = {
			color: "primary",
			properties: null,
			tooltipText: "",
			track: Ev
		}, kv = {
			start: -0,
			end: -0,
			detail: { devtools: Ov }
		}, Av = ["Changed Props", ""], jv = "This component received deeply equal props. It might benefit from useMemo or the React Compiler in its owner.", Mv = ["Changed Props", jv], Nv = 1, Pv = 2, Fv = [], Iv = 0, Lv = 0, Rv = {};
		Object.freeze(Rv);
		var zv = null, Bv = null, U = 0, Vv = 1, W = 2, Hv = 8, Uv = 16, Wv = 32, Gv = !1;
		try {
			Object.preventExtensions({});
		} catch {
			Gv = !0;
		}
		var Kv = /* @__PURE__ */ new WeakMap(), qv = [], Jv = 0, Yv = null, Xv = 0, Zv = [], Qv = 0, $v = null, ey = 1, ty = "", ny = null, ry = null, G = !1, iy = !1, ay = null, oy = null, sy = !1, cy = Error("Hydration Mismatch Exception: This is not a real error, and should not leak into userspace. If you're seeing this, it's likely a bug in React."), ly = ye(null), uy = ye(null), dy = {}, fy = null, py = null, my = !1, hy = typeof AbortController < "u" ? AbortController : function() {
			var e = [], t = this.signal = {
				aborted: !1,
				addEventListener: function(t, n) {
					e.push(n);
				}
			};
			this.abort = function() {
				t.aborted = !0, e.forEach(function(e) {
					return e();
				});
			};
		}, gy = Cm.unstable_scheduleCallback, _y = Cm.unstable_NormalPriority, vy = {
			$$typeof: Fm,
			Consumer: null,
			Provider: null,
			_currentValue: null,
			_currentValue2: null,
			_threadCount: 0,
			_currentRenderer: null,
			_currentRenderer2: null
		}, yy = null, by = Cm.unstable_now, xy = console.createTask ? console.createTask : function() {
			return null;
		}, Sy = 1, Cy = 2, wy = -0, Ty = -0, Ey = -0, Dy = null, Oy = -1.1, ky = -0, Ay = -0, K = -1.1, q = -1.1, jy = null, My = !1, Ny = -0, Py = -1.1, Fy = null, Iy = 0, Ly = null, Ry = null, zy = -1.1, By = null, Vy = -1.1, Hy = -1.1, Uy = -0, Wy = -1.1, Gy = -1.1, Ky = 0, qy = null, Jy = null, Yy = null, Xy = -1.1, Zy = null, Qy = -1.1, $y = -1.1, eb = -0, tb = -0, nb = 0, rb = null, ib = 0, ab = -1.1, ob = !1, sb = !1, cb = null, lb = 0, ub = 0, db = null, fb = R.S;
		R.S = function(e, t) {
			if (BC = Sh(), typeof t == "object" && t && typeof t.then == "function") {
				if (0 > Wy && 0 > Gy) {
					Wy = by();
					var n = rf(), r = nf();
					(n !== Qy || r !== Zy) && (Qy = -1.1), Xy = n, Zy = r;
				}
				Qi(e, t);
			}
			if (yy !== null) for (n = jw; n !== null;) ji(n, yy), n = n.next;
			if (n = e.types, n !== null) {
				for (r = jw; r !== null;) ji(r, n), r = r.next;
				if (ub !== 0) {
					r = yy, r === null && (r = yy = []);
					for (var i = 0; i < n.length; i++) {
						var a = n[i];
						r.indexOf(a) === -1 && r.push(a);
					}
				}
			}
			fb !== null && fb(e, t);
		};
		var pb = ye(null), mb = {
			recordUnsafeLifecycleWarnings: function() {},
			flushPendingUnsafeLifecycleWarnings: function() {},
			recordLegacyContextWarning: function() {},
			flushLegacyContextWarning: function() {},
			discardPendingWarnings: function() {}
		}, hb = [], gb = [], _b = [], vb = [], yb = [], bb = [], xb = /* @__PURE__ */ new Set();
		mb.recordUnsafeLifecycleWarnings = function(e, t) {
			xb.has(e.type) || (typeof t.componentWillMount == "function" && !0 !== t.componentWillMount.__suppressDeprecationWarning && hb.push(e), e.mode & Hv && typeof t.UNSAFE_componentWillMount == "function" && gb.push(e), typeof t.componentWillReceiveProps == "function" && !0 !== t.componentWillReceiveProps.__suppressDeprecationWarning && _b.push(e), e.mode & Hv && typeof t.UNSAFE_componentWillReceiveProps == "function" && vb.push(e), typeof t.componentWillUpdate == "function" && !0 !== t.componentWillUpdate.__suppressDeprecationWarning && yb.push(e), e.mode & Hv && typeof t.UNSAFE_componentWillUpdate == "function" && bb.push(e));
		}, mb.flushPendingUnsafeLifecycleWarnings = function() {
			var e = /* @__PURE__ */ new Set();
			0 < hb.length && (hb.forEach(function(t) {
				e.add(S(t) || "Component"), xb.add(t.type);
			}), hb = []);
			var t = /* @__PURE__ */ new Set();
			0 < gb.length && (gb.forEach(function(e) {
				t.add(S(e) || "Component"), xb.add(e.type);
			}), gb = []);
			var n = /* @__PURE__ */ new Set();
			0 < _b.length && (_b.forEach(function(e) {
				n.add(S(e) || "Component"), xb.add(e.type);
			}), _b = []);
			var r = /* @__PURE__ */ new Set();
			0 < vb.length && (vb.forEach(function(e) {
				r.add(S(e) || "Component"), xb.add(e.type);
			}), vb = []);
			var i = /* @__PURE__ */ new Set();
			0 < yb.length && (yb.forEach(function(e) {
				i.add(S(e) || "Component"), xb.add(e.type);
			}), yb = []);
			var a = /* @__PURE__ */ new Set();
			if (0 < bb.length && (bb.forEach(function(e) {
				a.add(S(e) || "Component"), xb.add(e.type);
			}), bb = []), 0 < t.size) {
				var o = h(t);
				console.error("Using UNSAFE_componentWillMount in strict mode is not recommended and may indicate bugs in your code. See https://react.dev/link/unsafe-component-lifecycles for details.\n\n* Move code with side effects to componentDidMount, and set initial state in the constructor.\n\nPlease update the following components: %s", o);
			}
			0 < r.size && (o = h(r), console.error("Using UNSAFE_componentWillReceiveProps in strict mode is not recommended and may indicate bugs in your code. See https://react.dev/link/unsafe-component-lifecycles for details.\n\n* Move data fetching code or side effects to componentDidUpdate.\n* If you're updating state whenever props change, refactor your code to use memoization techniques or move it to static getDerivedStateFromProps. Learn more at: https://react.dev/link/derived-state\n\nPlease update the following components: %s", o)), 0 < a.size && (o = h(a), console.error("Using UNSAFE_componentWillUpdate in strict mode is not recommended and may indicate bugs in your code. See https://react.dev/link/unsafe-component-lifecycles for details.\n\n* Move data fetching code or side effects to componentDidUpdate.\n\nPlease update the following components: %s", o)), 0 < e.size && (o = h(e), console.warn("componentWillMount has been renamed, and is not recommended for use. See https://react.dev/link/unsafe-component-lifecycles for details.\n\n* Move code with side effects to componentDidMount, and set initial state in the constructor.\n* Rename componentWillMount to UNSAFE_componentWillMount to suppress this warning in non-strict mode. In React 18.x, only the UNSAFE_ name will work. To rename all deprecated lifecycles to their new names, you can run `npx react-codemod rename-unsafe-lifecycles` in your project source folder.\n\nPlease update the following components: %s", o)), 0 < n.size && (o = h(n), console.warn("componentWillReceiveProps has been renamed, and is not recommended for use. See https://react.dev/link/unsafe-component-lifecycles for details.\n\n* Move data fetching code or side effects to componentDidUpdate.\n* If you're updating state whenever props change, refactor your code to use memoization techniques or move it to static getDerivedStateFromProps. Learn more at: https://react.dev/link/derived-state\n* Rename componentWillReceiveProps to UNSAFE_componentWillReceiveProps to suppress this warning in non-strict mode. In React 18.x, only the UNSAFE_ name will work. To rename all deprecated lifecycles to their new names, you can run `npx react-codemod rename-unsafe-lifecycles` in your project source folder.\n\nPlease update the following components: %s", o)), 0 < i.size && (o = h(i), console.warn("componentWillUpdate has been renamed, and is not recommended for use. See https://react.dev/link/unsafe-component-lifecycles for details.\n\n* Move data fetching code or side effects to componentDidUpdate.\n* Rename componentWillUpdate to UNSAFE_componentWillUpdate to suppress this warning in non-strict mode. In React 18.x, only the UNSAFE_ name will work. To rename all deprecated lifecycles to their new names, you can run `npx react-codemod rename-unsafe-lifecycles` in your project source folder.\n\nPlease update the following components: %s", o));
		};
		var Sb = /* @__PURE__ */ new Map(), Cb = /* @__PURE__ */ new Set();
		mb.recordLegacyContextWarning = function(e, t) {
			for (var n = null, r = e; r !== null;) r.mode & Hv && (n = r), r = r.return;
			n === null ? console.error("Expected to find a StrictMode component in a strict mode tree. This error is likely caused by a bug in React. Please file an issue.") : !Cb.has(e.type) && (r = Sb.get(n), e.type.contextTypes != null || e.type.childContextTypes != null || t !== null && typeof t.getChildContext == "function") && (r === void 0 && (r = [], Sb.set(n, r)), r.push(e));
		}, mb.flushLegacyContextWarning = function() {
			Sb.forEach(function(e) {
				if (e.length !== 0) {
					var t = e[0], n = /* @__PURE__ */ new Set();
					e.forEach(function(e) {
						n.add(S(e) || "Component"), Cb.add(e.type);
					});
					var r = h(n);
					w(t, function() {
						console.error("Legacy context API has been detected within a strict-mode tree.\n\nThe old API will be supported in all 16.x releases, but applications using it should migrate to the new version.\n\nPlease update the following components: %s\n\nLearn more about this warning here: https://react.dev/link/legacy-context", r);
					});
				}
			});
		}, mb.discardPendingWarnings = function() {
			hb = [], gb = [], _b = [], vb = [], yb = [], bb = [], Sb = /* @__PURE__ */ new Map();
		};
		var wb = "", Tb = { react_stack_bottom_frame: function(e, t, n) {
			var r = gh;
			gh = !0;
			try {
				return e(t, n);
			} finally {
				gh = r;
			}
		} }, Eb = Tb.react_stack_bottom_frame.bind(Tb), Db = { react_stack_bottom_frame: function(e) {
			var t = gh;
			gh = !0;
			try {
				return e.render();
			} finally {
				gh = t;
			}
		} }, Ob = Db.react_stack_bottom_frame.bind(Db), kb = { react_stack_bottom_frame: function(e, t) {
			try {
				t.componentDidMount();
			} catch (t) {
				Ku(e, e.return, t);
			}
		} }, Ab = kb.react_stack_bottom_frame.bind(kb), jb = { react_stack_bottom_frame: function(e, t, n, r, i) {
			try {
				t.componentDidUpdate(n, r, i);
			} catch (t) {
				Ku(e, e.return, t);
			}
		} }, Mb = jb.react_stack_bottom_frame.bind(jb), Nb = { react_stack_bottom_frame: function(e, t) {
			var n = t.stack;
			e.componentDidCatch(t.value, { componentStack: n === null ? "" : n });
		} }, Pb = Nb.react_stack_bottom_frame.bind(Nb), Fb = { react_stack_bottom_frame: function(e, t, n) {
			try {
				n.componentWillUnmount();
			} catch (n) {
				Ku(e, t, n);
			}
		} }, Ib = Fb.react_stack_bottom_frame.bind(Fb), Lb = { react_stack_bottom_frame: function(e) {
			var t = e.create;
			return e = e.inst, t = t(), e.destroy = t;
		} }, Rb = Lb.react_stack_bottom_frame.bind(Lb), zb = { react_stack_bottom_frame: function(e, t, n) {
			try {
				n();
			} catch (n) {
				Ku(e, t, n);
			}
		} }, Bb = zb.react_stack_bottom_frame.bind(zb), Vb = { react_stack_bottom_frame: function(e) {
			var t = e._init;
			return t(e._payload);
		} }, Hb = Vb.react_stack_bottom_frame.bind(Vb), Ub = Error("Suspense Exception: This is not a real error! It's an implementation detail of `use` to interrupt the current render. You must either rethrow it immediately, or move the `use` call outside of the `try/catch` block. Capturing without rethrowing will lead to unexpected behavior.\n\nTo handle async errors, wrap your component in an error boundary, or call the promise's `.catch` method and pass the result to `use`."), Wb = Error("Suspense Exception: This is not a real error, and should not leak into userspace. If you're seeing this, it's likely a bug in React."), Gb = Error("Suspense Exception: This is not a real error! It's an implementation detail of `useActionState` to interrupt the current render. You must either rethrow it immediately, or move the `useActionState` call outside of the `try/catch` block. Capturing without rethrowing will lead to unexpected behavior.\n\nTo handle async errors, wrap your component in an error boundary."), Kb = { then: function() {
			console.error("Internal React error: A listener was unexpectedly attached to a \"noop\" thenable. This is a bug in React. Please file an issue.");
		} }, qb = null, Jb = null, Yb = !1, Xb = null, Zb = !1, Qb = null, $b = 0, J = null, ex, tx = ex = !1, nx = {}, rx = {}, ix = {};
		m = function(e, t, n) {
			if (typeof n == "object" && n && n._store && (!n._store.validated && n.key == null || n._store.validated === 2)) {
				if (typeof n._store != "object") throw Error("React Component in warnForMissingKey should have a _store. This error is likely caused by a bug in React. Please file an issue.");
				n._store.validated = 1;
				var r = S(e), i = r || "null";
				if (!nx[i]) {
					nx[i] = !0, n = n._owner, e = e._debugOwner;
					var a = "";
					e && typeof e.tag == "number" && (i = S(e)) && (a = "\n\nCheck the render method of `" + i + "`."), a || r && (a = "\n\nCheck the top-level render call using <" + r + ">.");
					var o = "";
					n != null && e !== n && (r = null, typeof n.tag == "number" ? r = S(n) : typeof n.name == "string" && (r = n.name), r && (o = " It was passed a child from " + r + ".")), w(t, function() {
						console.error("Each child in a list should have a unique \"key\" prop.%s%s See https://react.dev/link/warning-keys for more information.", a, o);
					});
				}
			}
		};
		var ax = xa(!0), ox = xa(!1), sx = 0, cx = 1, lx = 2, ux = 3, dx = !1, fx = !1, px = null, mx = !1, hx = ye(null), gx = ye(0), _x = ye(null), vx = null, yx = 1, bx = 2, xx = ye(0), Sx = 0, Cx = 1, wx = 2, Tx = 4, Ex = 8, Dx, Ox = /* @__PURE__ */ new Set(), kx = /* @__PURE__ */ new Set(), Ax = /* @__PURE__ */ new Set(), jx = /* @__PURE__ */ new Set(), Mx = 0, Y = null, Nx = null, Px = null, Fx = !1, Ix = !1, Lx = !1, Rx = 0, zx = 0, Bx = null, Vx = 0, Hx = 25, X = null, Ux = null, Wx = -1, Gx = !1, Kx = {
			readContext: Ti,
			use: ao,
			useCallback: qa,
			useContext: qa,
			useEffect: qa,
			useImperativeHandle: qa,
			useLayoutEffect: qa,
			useInsertionEffect: qa,
			useMemo: qa,
			useReducer: qa,
			useRef: qa,
			useState: qa,
			useDebugValue: qa,
			useDeferredValue: qa,
			useTransition: qa,
			useSyncExternalStore: qa,
			useId: qa,
			useHostTransitionStatus: qa,
			useFormState: qa,
			useActionState: qa,
			useOptimistic: qa,
			useMemoCache: qa,
			useCacheRefresh: qa,
			useEffectEvent: qa
		}, qx = null, Jx = null, Yx = null, Xx = null, Zx = null, Qx = null, $x = null;
		qx = {
			readContext: function(e) {
				return Ti(e);
			},
			use: ao,
			useCallback: function(e, t) {
				return X = "useCallback", D(), Ga(t), Xo(e, t);
			},
			useContext: function(e) {
				return X = "useContext", D(), Ti(e);
			},
			useEffect: function(e, t) {
				return X = "useEffect", D(), Ga(t), Ho(e, t);
			},
			useImperativeHandle: function(e, t, n) {
				return X = "useImperativeHandle", D(), Ga(n), Jo(e, t, n);
			},
			useInsertionEffect: function(e, t) {
				X = "useInsertionEffect", D(), Ga(t), Bo(4, wx, e, t);
			},
			useLayoutEffect: function(e, t) {
				return X = "useLayoutEffect", D(), Ga(t), Ko(e, t);
			},
			useMemo: function(e, t) {
				X = "useMemo", D(), Ga(t);
				var n = R.H;
				R.H = Zx;
				try {
					return Qo(e, t);
				} finally {
					R.H = n;
				}
			},
			useReducer: function(e, t, n) {
				X = "useReducer", D();
				var r = R.H;
				R.H = Zx;
				try {
					return co(e, t, n);
				} finally {
					R.H = r;
				}
			},
			useRef: function(e) {
				return X = "useRef", D(), zo(e);
			},
			useState: function(e) {
				X = "useState", D();
				var t = R.H;
				R.H = Zx;
				try {
					return xo(e);
				} finally {
					R.H = t;
				}
			},
			useDebugValue: function() {
				X = "useDebugValue", D();
			},
			useDeferredValue: function(e, t) {
				return X = "useDeferredValue", D(), A(e, t);
			},
			useTransition: function() {
				return X = "useTransition", D(), ls();
			},
			useSyncExternalStore: function(e, t, n) {
				return X = "useSyncExternalStore", D(), po(e, t, n);
			},
			useId: function() {
				return X = "useId", D(), ps();
			},
			useFormState: function(e, t) {
				return X = "useFormState", D(), Ka(), No(e, t);
			},
			useActionState: function(e, t) {
				return X = "useActionState", D(), No(e, t);
			},
			useOptimistic: function(e) {
				return X = "useOptimistic", D(), So(e);
			},
			useHostTransitionStatus: fs,
			useMemoCache: oo,
			useCacheRefresh: function() {
				return X = "useCacheRefresh", D(), ms();
			},
			useEffectEvent: function(e) {
				return X = "useEffectEvent", D(), Wo(e);
			}
		}, Jx = {
			readContext: function(e) {
				return Ti(e);
			},
			use: ao,
			useCallback: function(e, t) {
				return X = "useCallback", O(), Xo(e, t);
			},
			useContext: function(e) {
				return X = "useContext", O(), Ti(e);
			},
			useEffect: function(e, t) {
				return X = "useEffect", O(), Ho(e, t);
			},
			useImperativeHandle: function(e, t, n) {
				return X = "useImperativeHandle", O(), Jo(e, t, n);
			},
			useInsertionEffect: function(e, t) {
				X = "useInsertionEffect", O(), Bo(4, wx, e, t);
			},
			useLayoutEffect: function(e, t) {
				return X = "useLayoutEffect", O(), Ko(e, t);
			},
			useMemo: function(e, t) {
				X = "useMemo", O();
				var n = R.H;
				R.H = Zx;
				try {
					return Qo(e, t);
				} finally {
					R.H = n;
				}
			},
			useReducer: function(e, t, n) {
				X = "useReducer", O();
				var r = R.H;
				R.H = Zx;
				try {
					return co(e, t, n);
				} finally {
					R.H = r;
				}
			},
			useRef: function(e) {
				return X = "useRef", O(), zo(e);
			},
			useState: function(e) {
				X = "useState", O();
				var t = R.H;
				R.H = Zx;
				try {
					return xo(e);
				} finally {
					R.H = t;
				}
			},
			useDebugValue: function() {
				X = "useDebugValue", O();
			},
			useDeferredValue: function(e, t) {
				return X = "useDeferredValue", O(), A(e, t);
			},
			useTransition: function() {
				return X = "useTransition", O(), ls();
			},
			useSyncExternalStore: function(e, t, n) {
				return X = "useSyncExternalStore", O(), po(e, t, n);
			},
			useId: function() {
				return X = "useId", O(), ps();
			},
			useActionState: function(e, t) {
				return X = "useActionState", O(), No(e, t);
			},
			useFormState: function(e, t) {
				return X = "useFormState", O(), Ka(), No(e, t);
			},
			useOptimistic: function(e) {
				return X = "useOptimistic", O(), So(e);
			},
			useHostTransitionStatus: fs,
			useMemoCache: oo,
			useCacheRefresh: function() {
				return X = "useCacheRefresh", O(), ms();
			},
			useEffectEvent: function(e) {
				return X = "useEffectEvent", O(), Wo(e);
			}
		}, Yx = {
			readContext: function(e) {
				return Ti(e);
			},
			use: ao,
			useCallback: function(e, t) {
				return X = "useCallback", O(), Zo(e, t);
			},
			useContext: function(e) {
				return X = "useContext", O(), Ti(e);
			},
			useEffect: function(e, t) {
				X = "useEffect", O(), Vo(2048, Ex, e, t);
			},
			useImperativeHandle: function(e, t, n) {
				return X = "useImperativeHandle", O(), Yo(e, t, n);
			},
			useInsertionEffect: function(e, t) {
				return X = "useInsertionEffect", O(), Vo(4, wx, e, t);
			},
			useLayoutEffect: function(e, t) {
				return X = "useLayoutEffect", O(), Vo(4, Tx, e, t);
			},
			useMemo: function(e, t) {
				X = "useMemo", O();
				var n = R.H;
				R.H = Qx;
				try {
					return $o(e, t);
				} finally {
					R.H = n;
				}
			},
			useReducer: function(e, t, n) {
				X = "useReducer", O();
				var r = R.H;
				R.H = Qx;
				try {
					return lo(e, t, n);
				} finally {
					R.H = r;
				}
			},
			useRef: function() {
				return X = "useRef", O(), k().memoizedState;
			},
			useState: function() {
				X = "useState", O();
				var e = R.H;
				R.H = Qx;
				try {
					return lo(so);
				} finally {
					R.H = e;
				}
			},
			useDebugValue: function() {
				X = "useDebugValue", O();
			},
			useDeferredValue: function(e, t) {
				return X = "useDeferredValue", O(), es(e, t);
			},
			useTransition: function() {
				return X = "useTransition", O(), us();
			},
			useSyncExternalStore: function(e, t, n) {
				return X = "useSyncExternalStore", O(), mo(e, t, n);
			},
			useId: function() {
				return X = "useId", O(), k().memoizedState;
			},
			useFormState: function(e) {
				return X = "useFormState", O(), Ka(), Po(e);
			},
			useActionState: function(e) {
				return X = "useActionState", O(), Po(e);
			},
			useOptimistic: function(e, t) {
				return X = "useOptimistic", O(), Co(e, t);
			},
			useHostTransitionStatus: fs,
			useMemoCache: oo,
			useCacheRefresh: function() {
				return X = "useCacheRefresh", O(), k().memoizedState;
			},
			useEffectEvent: function(e) {
				return X = "useEffectEvent", O(), Go(e);
			}
		}, Xx = {
			readContext: function(e) {
				return Ti(e);
			},
			use: ao,
			useCallback: function(e, t) {
				return X = "useCallback", O(), Zo(e, t);
			},
			useContext: function(e) {
				return X = "useContext", O(), Ti(e);
			},
			useEffect: function(e, t) {
				X = "useEffect", O(), Vo(2048, Ex, e, t);
			},
			useImperativeHandle: function(e, t, n) {
				return X = "useImperativeHandle", O(), Yo(e, t, n);
			},
			useInsertionEffect: function(e, t) {
				return X = "useInsertionEffect", O(), Vo(4, wx, e, t);
			},
			useLayoutEffect: function(e, t) {
				return X = "useLayoutEffect", O(), Vo(4, Tx, e, t);
			},
			useMemo: function(e, t) {
				X = "useMemo", O();
				var n = R.H;
				R.H = $x;
				try {
					return $o(e, t);
				} finally {
					R.H = n;
				}
			},
			useReducer: function(e, t, n) {
				X = "useReducer", O();
				var r = R.H;
				R.H = $x;
				try {
					return fo(e, t, n);
				} finally {
					R.H = r;
				}
			},
			useRef: function() {
				return X = "useRef", O(), k().memoizedState;
			},
			useState: function() {
				X = "useState", O();
				var e = R.H;
				R.H = $x;
				try {
					return fo(so);
				} finally {
					R.H = e;
				}
			},
			useDebugValue: function() {
				X = "useDebugValue", O();
			},
			useDeferredValue: function(e, t) {
				return X = "useDeferredValue", O(), ts(e, t);
			},
			useTransition: function() {
				return X = "useTransition", O(), ds();
			},
			useSyncExternalStore: function(e, t, n) {
				return X = "useSyncExternalStore", O(), mo(e, t, n);
			},
			useId: function() {
				return X = "useId", O(), k().memoizedState;
			},
			useFormState: function(e) {
				return X = "useFormState", O(), Ka(), Lo(e);
			},
			useActionState: function(e) {
				return X = "useActionState", O(), Lo(e);
			},
			useOptimistic: function(e, t) {
				return X = "useOptimistic", O(), To(e, t);
			},
			useHostTransitionStatus: fs,
			useMemoCache: oo,
			useCacheRefresh: function() {
				return X = "useCacheRefresh", O(), k().memoizedState;
			},
			useEffectEvent: function(e) {
				return X = "useEffectEvent", O(), Go(e);
			}
		}, Zx = {
			readContext: function(e) {
				return f(), Ti(e);
			},
			use: function(e) {
				return d(), ao(e);
			},
			useCallback: function(e, t) {
				return X = "useCallback", d(), D(), Xo(e, t);
			},
			useContext: function(e) {
				return X = "useContext", d(), D(), Ti(e);
			},
			useEffect: function(e, t) {
				return X = "useEffect", d(), D(), Ho(e, t);
			},
			useImperativeHandle: function(e, t, n) {
				return X = "useImperativeHandle", d(), D(), Jo(e, t, n);
			},
			useInsertionEffect: function(e, t) {
				X = "useInsertionEffect", d(), D(), Bo(4, wx, e, t);
			},
			useLayoutEffect: function(e, t) {
				return X = "useLayoutEffect", d(), D(), Ko(e, t);
			},
			useMemo: function(e, t) {
				X = "useMemo", d(), D();
				var n = R.H;
				R.H = Zx;
				try {
					return Qo(e, t);
				} finally {
					R.H = n;
				}
			},
			useReducer: function(e, t, n) {
				X = "useReducer", d(), D();
				var r = R.H;
				R.H = Zx;
				try {
					return co(e, t, n);
				} finally {
					R.H = r;
				}
			},
			useRef: function(e) {
				return X = "useRef", d(), D(), zo(e);
			},
			useState: function(e) {
				X = "useState", d(), D();
				var t = R.H;
				R.H = Zx;
				try {
					return xo(e);
				} finally {
					R.H = t;
				}
			},
			useDebugValue: function() {
				X = "useDebugValue", d(), D();
			},
			useDeferredValue: function(e, t) {
				return X = "useDeferredValue", d(), D(), A(e, t);
			},
			useTransition: function() {
				return X = "useTransition", d(), D(), ls();
			},
			useSyncExternalStore: function(e, t, n) {
				return X = "useSyncExternalStore", d(), D(), po(e, t, n);
			},
			useId: function() {
				return X = "useId", d(), D(), ps();
			},
			useFormState: function(e, t) {
				return X = "useFormState", d(), D(), No(e, t);
			},
			useActionState: function(e, t) {
				return X = "useActionState", d(), D(), No(e, t);
			},
			useOptimistic: function(e) {
				return X = "useOptimistic", d(), D(), So(e);
			},
			useMemoCache: function(e) {
				return d(), oo(e);
			},
			useHostTransitionStatus: fs,
			useCacheRefresh: function() {
				return X = "useCacheRefresh", D(), ms();
			},
			useEffectEvent: function(e) {
				return X = "useEffectEvent", d(), D(), Wo(e);
			}
		}, Qx = {
			readContext: function(e) {
				return f(), Ti(e);
			},
			use: function(e) {
				return d(), ao(e);
			},
			useCallback: function(e, t) {
				return X = "useCallback", d(), O(), Zo(e, t);
			},
			useContext: function(e) {
				return X = "useContext", d(), O(), Ti(e);
			},
			useEffect: function(e, t) {
				X = "useEffect", d(), O(), Vo(2048, Ex, e, t);
			},
			useImperativeHandle: function(e, t, n) {
				return X = "useImperativeHandle", d(), O(), Yo(e, t, n);
			},
			useInsertionEffect: function(e, t) {
				return X = "useInsertionEffect", d(), O(), Vo(4, wx, e, t);
			},
			useLayoutEffect: function(e, t) {
				return X = "useLayoutEffect", d(), O(), Vo(4, Tx, e, t);
			},
			useMemo: function(e, t) {
				X = "useMemo", d(), O();
				var n = R.H;
				R.H = Qx;
				try {
					return $o(e, t);
				} finally {
					R.H = n;
				}
			},
			useReducer: function(e, t, n) {
				X = "useReducer", d(), O();
				var r = R.H;
				R.H = Qx;
				try {
					return lo(e, t, n);
				} finally {
					R.H = r;
				}
			},
			useRef: function() {
				return X = "useRef", d(), O(), k().memoizedState;
			},
			useState: function() {
				X = "useState", d(), O();
				var e = R.H;
				R.H = Qx;
				try {
					return lo(so);
				} finally {
					R.H = e;
				}
			},
			useDebugValue: function() {
				X = "useDebugValue", d(), O();
			},
			useDeferredValue: function(e, t) {
				return X = "useDeferredValue", d(), O(), es(e, t);
			},
			useTransition: function() {
				return X = "useTransition", d(), O(), us();
			},
			useSyncExternalStore: function(e, t, n) {
				return X = "useSyncExternalStore", d(), O(), mo(e, t, n);
			},
			useId: function() {
				return X = "useId", d(), O(), k().memoizedState;
			},
			useFormState: function(e) {
				return X = "useFormState", d(), O(), Po(e);
			},
			useActionState: function(e) {
				return X = "useActionState", d(), O(), Po(e);
			},
			useOptimistic: function(e, t) {
				return X = "useOptimistic", d(), O(), Co(e, t);
			},
			useMemoCache: function(e) {
				return d(), oo(e);
			},
			useHostTransitionStatus: fs,
			useCacheRefresh: function() {
				return X = "useCacheRefresh", O(), k().memoizedState;
			},
			useEffectEvent: function(e) {
				return X = "useEffectEvent", d(), O(), Go(e);
			}
		}, $x = {
			readContext: function(e) {
				return f(), Ti(e);
			},
			use: function(e) {
				return d(), ao(e);
			},
			useCallback: function(e, t) {
				return X = "useCallback", d(), O(), Zo(e, t);
			},
			useContext: function(e) {
				return X = "useContext", d(), O(), Ti(e);
			},
			useEffect: function(e, t) {
				X = "useEffect", d(), O(), Vo(2048, Ex, e, t);
			},
			useImperativeHandle: function(e, t, n) {
				return X = "useImperativeHandle", d(), O(), Yo(e, t, n);
			},
			useInsertionEffect: function(e, t) {
				return X = "useInsertionEffect", d(), O(), Vo(4, wx, e, t);
			},
			useLayoutEffect: function(e, t) {
				return X = "useLayoutEffect", d(), O(), Vo(4, Tx, e, t);
			},
			useMemo: function(e, t) {
				X = "useMemo", d(), O();
				var n = R.H;
				R.H = Qx;
				try {
					return $o(e, t);
				} finally {
					R.H = n;
				}
			},
			useReducer: function(e, t, n) {
				X = "useReducer", d(), O();
				var r = R.H;
				R.H = Qx;
				try {
					return fo(e, t, n);
				} finally {
					R.H = r;
				}
			},
			useRef: function() {
				return X = "useRef", d(), O(), k().memoizedState;
			},
			useState: function() {
				X = "useState", d(), O();
				var e = R.H;
				R.H = Qx;
				try {
					return fo(so);
				} finally {
					R.H = e;
				}
			},
			useDebugValue: function() {
				X = "useDebugValue", d(), O();
			},
			useDeferredValue: function(e, t) {
				return X = "useDeferredValue", d(), O(), ts(e, t);
			},
			useTransition: function() {
				return X = "useTransition", d(), O(), ds();
			},
			useSyncExternalStore: function(e, t, n) {
				return X = "useSyncExternalStore", d(), O(), mo(e, t, n);
			},
			useId: function() {
				return X = "useId", d(), O(), k().memoizedState;
			},
			useFormState: function(e) {
				return X = "useFormState", d(), O(), Lo(e);
			},
			useActionState: function(e) {
				return X = "useActionState", d(), O(), Lo(e);
			},
			useOptimistic: function(e, t) {
				return X = "useOptimistic", d(), O(), To(e, t);
			},
			useMemoCache: function(e) {
				return d(), oo(e);
			},
			useHostTransitionStatus: fs,
			useCacheRefresh: function() {
				return X = "useCacheRefresh", O(), k().memoizedState;
			},
			useEffectEvent: function(e) {
				return X = "useEffectEvent", d(), O(), Go(e);
			}
		};
		var eS = {}, tS = /* @__PURE__ */ new Set(), nS = /* @__PURE__ */ new Set(), rS = /* @__PURE__ */ new Set(), iS = /* @__PURE__ */ new Set(), aS = /* @__PURE__ */ new Set(), oS = /* @__PURE__ */ new Set(), sS = /* @__PURE__ */ new Set(), cS = /* @__PURE__ */ new Set(), lS = /* @__PURE__ */ new Set(), uS = /* @__PURE__ */ new Set();
		Object.freeze(eS);
		var dS = {
			enqueueSetState: function(e, t, n) {
				e = e._reactInternals;
				var r = iu(e), i = Ta(r);
				i.payload = t, n != null && (Cs(n), i.callback = n), t = Ea(e, i, r), t !== null && (Ni(r, "this.setState()", e), su(t, e, r), Da(t, e, r));
			},
			enqueueReplaceState: function(e, t, n) {
				e = e._reactInternals;
				var r = iu(e), i = Ta(r);
				i.tag = cx, i.payload = t, n != null && (Cs(n), i.callback = n), t = Ea(e, i, r), t !== null && (Ni(r, "this.replaceState()", e), su(t, e, r), Da(t, e, r));
			},
			enqueueForceUpdate: function(e, t) {
				e = e._reactInternals;
				var n = iu(e), r = Ta(n);
				r.tag = lx, t != null && (Cs(t), r.callback = t), t = Ea(e, r, n), t !== null && (Ni(n, "this.forceUpdate()", e), su(t, e, n), Da(t, e, n));
			}
		}, fS = null, pS = null, mS = Error("This is not a real error. It's an implementation detail of React's selective hydration feature. If this leaks into userspace, it's a bug in React. Please file an issue."), hS = !1, gS = {}, _S = {}, vS = {}, yS = {}, bS = !1, xS = {}, SS = {}, CS = {}, wS = {
			dehydrated: null,
			treeContext: null,
			retryLane: 0,
			hydrationErrors: null
		}, TS = !1, ES = null;
		ES = /* @__PURE__ */ new Set();
		var DS = !1, OS = null, kS = null, AS = 0, jS = /* @__PURE__ */ new Map(), MS = {}, NS = 0, PS = 1, FS = 2, IS = !1, LS = !1, RS = !1, zS = !1, BS = typeof WeakSet == "function" ? WeakSet : Set, VS = null, HS = null, US = null, WS = !1, GS = !1, KS = !1, qS = !1, JS = null, YS = !1, XS = null, ZS = !1, QS = 8192, $S = {
			getCacheForType: function(e) {
				var t = Ti(vy), n = t.data.get(e);
				return n === void 0 && (n = e(), t.data.set(e, n)), n;
			},
			cacheSignal: function() {
				return Ti(vy).controller.signal;
			},
			getOwner: function() {
				return hh;
			}
		};
		if (typeof Symbol == "function" && Symbol.for) {
			var eC = Symbol.for;
			eC("selector.component"), eC("selector.has_pseudo_class"), eC("selector.role"), eC("selector.test_id"), eC("selector.text");
		}
		var tC = [], nC = typeof WeakMap == "function" ? WeakMap : Map, rC = 0, iC = 2, aC = 4, oC = 0, sC = 1, cC = 2, lC = 3, uC = 4, dC = 6, fC = 5, Z = rC, pC = null, Q = null, $ = 0, mC = 0, hC = 1, gC = 2, _C = 3, vC = 4, yC = 5, bC = 6, xC = 7, SC = 8, CC = 9, wC = mC, TC = null, EC = !1, DC = !1, OC = !1, kC = 0, AC = oC, jC = 0, MC = 0, NC = 0, PC = 0, FC = 0, IC = null, LC = null, RC = !1, zC = 0, BC = 0, VC = 300, HC = Infinity, UC = 500, WC = null, GC = null, KC = null, qC = 0, JC = 1, YC = 2, XC = 3, ZC = 0, QC = 1, $C = 2, ew = 3, tw = 4, nw = 5, rw = 0, iw = null, aw = null, ow = 0, sw = 0, cw = -0, lw = null, uw = null, dw = null, fw = null, pw = null, mw = null, hw = qC, gw = null, _w = 50, vw = 0, yw = null, bw = !1, xw = !1, Sw = 50, Cw = 0, ww = null, Tw = !1, Ew = !1, Dw = null, Ow = !1, kw = /* @__PURE__ */ new Set(), Aw = {}, jw = null, Mw = null, Nw = !1, Pw = !1, Fw = !1, Iw = !1, Lw = 0, Rw = {};
		(function() {
			for (var e = 0; e < lv.length; e++) {
				var t = lv[e], n = t.toLowerCase();
				t = t[0].toUpperCase() + t.slice(1), ur(n, "on" + t);
			}
			ur(tv, "onAnimationEnd"), ur(nv, "onAnimationIteration"), ur(rv, "onAnimationStart"), ur("dblclick", "onDoubleClick"), ur("focusin", "onFocus"), ur("focusout", "onBlur"), ur(iv, "onTransitionRun"), ur(av, "onTransitionStart"), ur(ov, "onTransitionCancel"), ur(sv, "onTransitionEnd");
		})(), bt("onMouseEnter", ["mouseout", "mouseover"]), bt("onMouseLeave", ["mouseout", "mouseover"]), bt("onPointerEnter", ["pointerout", "pointerover"]), bt("onPointerLeave", ["pointerout", "pointerover"]), yt("onChange", "change click focusin focusout input keydown keyup selectionchange".split(" ")), yt("onSelect", "focusout contextmenu dragend focusin keydown keyup mousedown mouseup selectionchange".split(" ")), yt("onBeforeInput", [
			"compositionend",
			"keypress",
			"textInput",
			"paste"
		]), yt("onCompositionEnd", "compositionend focusout keydown keypress keyup mousedown".split(" ")), yt("onCompositionStart", "compositionstart focusout keydown keypress keyup mousedown".split(" ")), yt("onCompositionUpdate", "compositionupdate focusout keydown keypress keyup mousedown".split(" "));
		var zw = "abort canplay canplaythrough durationchange emptied encrypted ended error loadeddata loadedmetadata loadstart pause play playing progress ratechange resize seeked seeking stalled suspend timeupdate volumechange waiting".split(" "), Bw = new Set("beforetoggle cancel close invalid load scroll scrollend toggle".split(" ").concat(zw)), Vw = "_reactListening" + Math.random().toString(36).slice(2), Hw = !1, Uw = !1, Ww = !1, Gw = !1, Kw = !1, qw = !1, Jw = !1, Yw = {}, Xw = /\r\n?/g, Zw = /\u0000|\uFFFD/g, Qw = "http://www.w3.org/1999/xlink", $w = "http://www.w3.org/XML/1998/namespace", eT = {}, tT = "javascript:throw new Error('React form unexpectedly submitted.')", nT = "suppressHydrationWarning", rT = "&", iT = "/&", aT = "$", oT = "/$", sT = "$?", cT = "$~", lT = "$!", uT = "html", dT = "body", fT = "head", pT = "F!", mT = "F", hT = "loading", gT = "style", _T = 0, vT = 1, yT = 2, bT = null, xT = null, ST = !1, CT = {
			dialog: !0,
			webview: !0
		}, wT = null, TT = void 0, ET = typeof setTimeout == "function" ? setTimeout : void 0, DT = typeof clearTimeout == "function" ? clearTimeout : void 0, OT = -1, kT = typeof Promise == "function" ? Promise : void 0, AT = typeof requestAnimationFrame == "function" ? requestAnimationFrame : ET, jT = typeof queueMicrotask == "function" ? queueMicrotask : kT === void 0 ? ET : function(e) {
			return kT.resolve(null).then(e).catch(af);
		}, MT = 500;
		Nf.prototype.animate = function(e, t) {
			return t = typeof t == "number" ? { duration: t } : L({}, t), t.pseudoElement = this._selector, this._scope.animate(e, t);
		}, Nf.prototype.getAnimations = function() {
			for (var e = this._scope, t = this._selector, n = e.getAnimations({ subtree: !0 }), r = [], i = 0; i < n.length; i++) {
				var a = n[i].effect;
				a !== null && a.target === e && a.pseudoElement === t && r.push(n[i]);
			}
			return r;
		}, Nf.prototype.getComputedStyle = function() {
			return getComputedStyle(this._scope, this._selector);
		}, Ff.prototype.addEventListener = function(e, t, n) {
			var r = null, i = null;
			if (!(n != null && typeof n != "boolean" && (r = n.signal || null, r !== null && r.aborted))) {
				this._eventListeners === null && (this._eventListeners = []);
				var a = this._eventListeners;
				if (Bf(a, e, t, n) === -1) {
					var o = this, s = t;
					n != null && typeof n != "boolean" && !0 === n.once && (s = function(r) {
						o.removeEventListener(e, t, n), typeof t == "function" ? t.call(this, r) : t.handleEvent(r);
					}), r !== null && (i = o.removeEventListener.bind(o, e, t, n), r.addEventListener("abort", i, { once: !0 }), i = r.removeEventListener.bind(r, "abort", i)), r = Rf(n), a.push({
						type: e,
						listener: t,
						optionsOrUseCapture: n,
						attachedListener: s,
						cleanup: i
					}), x(this._fragmentFiber, If, e, s, r);
				}
				this._eventListeners = a;
			}
		}, Ff.prototype.removeEventListener = function(e, t, n) {
			var r = this._eventListeners;
			if (r !== null && (t = Bf(r, e, t, n), t !== -1)) {
				var i = r[t];
				n = i.attachedListener;
				var a = i.cleanup;
				i = Rf(i.optionsOrUseCapture), x(this._fragmentFiber, Lf, e, n, i), r.splice(t, 1), a !== null && a();
			}
		}, Ff.prototype.dispatchEvent = function(e) {
			var t = se(this._fragmentFiber);
			if (t === null) return !0;
			t = de(t);
			var n = this._eventListeners;
			if (n !== null && 0 < n.length || !e.bubbles) {
				var r = t.nodeType === 9 ? t.createComment("") : document.createTextNode("");
				if (n) for (var i = 0; i < n.length; i++) {
					var a = n[i];
					r.addEventListener(a.type, a.attachedListener, Rf(a.optionsOrUseCapture));
				}
				if (t.appendChild(r), e = r.dispatchEvent(e), n) for (i = 0; i < n.length; i++) a = n[i], r.removeEventListener(a.type, a.attachedListener, Rf(a.optionsOrUseCapture));
				return t.removeChild(r), e;
			}
			return t.dispatchEvent(e);
		}, Ff.prototype.focus = function(e) {
			oe(this._fragmentFiber.child, !0, Vf, e, void 0, void 0);
		}, Ff.prototype.focusLast = function(e) {
			var t = [];
			oe(this._fragmentFiber.child, !0, Hf, t, void 0, void 0);
			for (var n = t.length - 1; 0 <= n && !Vf(t[n], e); n--);
		}, Ff.prototype.blur = function() {
			var e = se(this._fragmentFiber);
			e !== null && (e = de(e), e = Yd(e).activeElement, e !== null && x(this._fragmentFiber, Uf, e));
		}, Ff.prototype.observeUsing = function(e) {
			var t = !1, n = !1;
			x(this._fragmentFiber, function(e) {
				if (e.tag === 6) t = !0;
				else return n = !0;
				return !1;
			}), t && !n && console.error("observeUsing() was called on a FragmentInstance with only text children. Observers do not work on text nodes."), this._observers === null && (this._observers = /* @__PURE__ */ new Set()), this._observers.add(e), x(this._fragmentFiber, Wf, e);
		}, Ff.prototype.unobserveUsing = function(e) {
			var t = this._observers;
			if (t !== null && t.has(e)) {
				t.delete(e), x(this._fragmentFiber, Gf, e);
				for (var n = t = 0; n < NT.length; n++) {
					var r = NT[n];
					r.fragmentInstance === this && r.observer === e ? e.unobserve(r.instance) : NT[t++] = r;
				}
				NT.length = t;
			} else console.error("You are calling unobserveUsing() with an observer that is not being observed with this fragment instance. First attach the observer with observeUsing()");
		};
		var NT = [], PT = !1;
		Ff.prototype.getClientRects = function() {
			var e = [];
			return x(this._fragmentFiber, qf, e), e;
		}, Ff.prototype.getRootNode = function(e) {
			var t = se(this._fragmentFiber);
			return t === null ? this : de(t).getRootNode(e);
		}, Ff.prototype.compareDocumentPosition = function(e) {
			var t = se(this._fragmentFiber);
			if (t === null) return Node.DOCUMENT_POSITION_DISCONNECTED;
			var n = [];
			x(this._fragmentFiber, Hf, n);
			var r = de(t);
			if (n.length === 0) {
				if (t = r, ce(this._fragmentFiber)) {
					a: {
						for (n = this._fragmentFiber.return; n !== null;) {
							if (n.tag === 4) {
								n = n.stateNode.containerInfo;
								break a;
							}
							if (n.tag === 3 || n.tag === 5 || n.tag === 27) break;
							n = n.return;
						}
						n = null;
					}
					n != null && (t = n);
				}
				n = this._fragmentFiber;
				var i = r = t.compareDocumentPosition(e);
				return t === e ? i = Node.DOCUMENT_POSITION_CONTAINS : r & Node.DOCUMENT_POSITION_CONTAINED_BY && (n = le(n)[1], n === null ? i = Node.DOCUMENT_POSITION_PRECEDING : (e = de(n).compareDocumentPosition(e), i = e === 0 || e & Node.DOCUMENT_POSITION_FOLLOWING ? Node.DOCUMENT_POSITION_FOLLOWING : Node.DOCUMENT_POSITION_PRECEDING)), i |= Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC;
			}
			t = de(n[0]), i = de(n[n.length - 1]);
			var a = ce(this._fragmentFiber) ? t.parentElement : r;
			if (a == null) return Node.DOCUMENT_POSITION_DISCONNECTED;
			r = a.compareDocumentPosition(t) & Node.DOCUMENT_POSITION_CONTAINED_BY, a = a.compareDocumentPosition(i) & Node.DOCUMENT_POSITION_CONTAINED_BY;
			var o = t.compareDocumentPosition(e), s = i.compareDocumentPosition(e), c = o & Node.DOCUMENT_POSITION_CONTAINED_BY || s & Node.DOCUMENT_POSITION_CONTAINED_BY;
			return s = r && a && o & Node.DOCUMENT_POSITION_FOLLOWING && s & Node.DOCUMENT_POSITION_PRECEDING, t = r && t === e || a && i === e || c || s ? Node.DOCUMENT_POSITION_CONTAINED_BY : !r && t === e || !a && i === e ? Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC : o, t & Node.DOCUMENT_POSITION_DISCONNECTED || t & Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC || Jf(t, this._fragmentFiber, n[0], n[n.length - 1], e) ? t : Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC;
		}, Ff.prototype.scrollIntoView = function(e) {
			if (typeof e == "object") throw Error("FragmentInstance.scrollIntoView() does not support scrollIntoViewOptions. Use the alignToTop boolean instead.");
			var t = [];
			x(this._fragmentFiber, Hf, t);
			var n = !1 !== e;
			if (t.length === 0) {
				var r = le(this._fragmentFiber);
				if (r = n ? r[1] || r[0] || se(this._fragmentFiber) : r[0] || r[1], r === null) return;
				if (r.tag === 6) {
					e = de(r), Yf(e, n);
					return;
				}
				if (r = de(r), r.nodeType !== 9) {
					if (r.nodeType === 11) {
						n = "host" in r ? r.host : null, n === null ? console.warn("You are attempting to scroll a FragmentInstance that is only mounted inside a detached DocumentFragment. No scroll was performed.") : n.scrollIntoView(e);
						return;
					}
					r.scrollIntoView(e);
				}
			}
			for (r = n ? t.length - 1 : 0; r !== (n ? -1 : t.length);) {
				var i = t[r];
				i.tag === 6 ? (i = de(i), Yf(i, n)) : de(i).scrollIntoView(e), r += n ? -1 : 1;
			}
		};
		var FT = null, IT = 0, LT = 1, RT = 2, zT = 3, BT = 4, VT = /* @__PURE__ */ new Map(), HT = /* @__PURE__ */ new Set(), UT = z.d;
		z.d = {
			f: function() {
				var e = UT.f(), t = fu();
				return e || t;
			},
			r: function(e) {
				var t = mt(e);
				t !== null && t.tag === 5 && t.type === "form" ? cs(t) : UT.r(e);
			},
			D: function(e) {
				UT.D(e), xp("dns-prefetch", e, null);
			},
			C: function(e, t) {
				UT.C(e, t), xp("preconnect", e, t);
			},
			L: function(e, t, n) {
				UT.L(e, t, n);
				var r = WT;
				if (r && e && t) {
					var i = "link[rel=\"preload\"][as=\"" + Nt(t) + "\"]";
					t === "image" && n && n.imageSrcSet ? (i += "[imagesrcset=\"" + Nt(n.imageSrcSet) + "\"]", typeof n.imageSizes == "string" && (i += "[imagesizes=\"" + Nt(n.imageSizes) + "\"]")) : i += "[href=\"" + Nt(e) + "\"]";
					var a = i;
					switch (t) {
						case "style":
							a = wp(e);
							break;
						case "script": a = Op(e);
					}
					if (!(VT.has(a) || (e = L({
						rel: "preload",
						href: t === "image" && n && n.imageSrcSet ? void 0 : e,
						as: t
					}, n), VT.set(a, e), r.querySelector(i) !== null || t === "style" && r.querySelector(Tp(a)) || t === "script" && r.querySelector(kp(a))))) {
						var o = r.createElement("link");
						Fd(o, "link", e), t === "style" && (o[eg] = !0, o.onload = o.onerror = function() {
							vt(o);
						}), _t(o), r.head.appendChild(o);
					}
				}
			},
			m: function(e, t) {
				UT.m(e, t);
				var n = WT;
				if (n && e) {
					var r = t && typeof t.as == "string" ? t.as : "script", i = "link[rel=\"modulepreload\"][as=\"" + Nt(r) + "\"][href=\"" + Nt(e) + "\"]", a = i;
					switch (r) {
						case "audioworklet":
						case "paintworklet":
						case "serviceworker":
						case "sharedworker":
						case "worker":
						case "script": a = Op(e);
					}
					if (!VT.has(a) && (e = L({
						rel: "modulepreload",
						href: e
					}, t), VT.set(a, e), n.querySelector(i) === null)) {
						switch (r) {
							case "audioworklet":
							case "paintworklet":
							case "serviceworker":
							case "sharedworker":
							case "worker":
							case "script": if (n.querySelector(kp(a))) return;
						}
						r = n.createElement("link"), Fd(r, "link", e), _t(r), n.head.appendChild(r);
					}
				}
			},
			X: function(e, t) {
				UT.X(e, t);
				var n = WT;
				if (n && e) {
					var r = gt(n).hoistableScripts, i = Op(e), a = r.get(i);
					a || (a = n.querySelector(kp(i)), a || (e = L({
						src: e,
						async: !0
					}, t), (t = VT.get(i)) && Np(e, t), a = n.createElement("script"), _t(a), Fd(a, "link", e), n.head.appendChild(a)), a = {
						type: "script",
						instance: a,
						count: 1,
						state: null
					}, r.set(i, a));
				}
			},
			S: function(e, t, n) {
				UT.S(e, t, n);
				var r = WT;
				if (r && e) {
					var i = gt(r).hoistableStyles, a = wp(e);
					t ||= "default";
					var o = i.get(a);
					if (!o) {
						var s = {
							loading: IT,
							preload: null
						};
						if (o = r.querySelector(Tp(a))) s.loading = LT | BT;
						else {
							e = L({
								rel: "stylesheet",
								href: e,
								"data-precedence": t
							}, n), (n = VT.get(a)) && Mp(e, n);
							var c = o = r.createElement("link");
							_t(c), Fd(c, "link", e), c._p = new Promise(function(e, t) {
								c.onload = e, c.onerror = t;
							}), c.addEventListener("load", function() {
								s.loading |= LT;
							}), c.addEventListener("error", function() {
								s.loading |= RT;
							}), s.loading |= BT, jp(o, t, r);
						}
						o = {
							type: "stylesheet",
							instance: o,
							count: 1,
							state: s
						}, i.set(a, o);
					}
				}
			},
			M: function(e, t) {
				UT.M(e, t);
				var n = WT;
				if (n && e) {
					var r = gt(n).hoistableScripts, i = Op(e), a = r.get(i);
					a || (a = n.querySelector(kp(i)), a || (e = L({
						src: e,
						async: !0,
						type: "module"
					}, t), (t = VT.get(i)) && Np(e, t), a = n.createElement("script"), _t(a), Fd(a, "link", e), n.head.appendChild(a)), a = {
						type: "script",
						instance: a,
						count: 1,
						state: null
					}, r.set(i, a));
				}
			}
		};
		var WT = typeof document > "u" ? null : document, GT = null, KT = 6e4, qT = 800, JT = 500, YT = 0, XT = null, ZT = null, QT = Ym, $T = {
			$$typeof: Fm,
			Provider: null,
			Consumer: null,
			_currentValue: QT,
			_currentValue2: QT,
			_threadCount: 0
		}, eE = "%c%s%c", tE = "background: #e6e6e6;background: light-dark(rgba(0,0,0,0.1), rgba(255,255,255,0.25));color: #000000;color: light-dark(#000000, #ffffff);border-radius: 2px", nE = "", rE = " ", iE = Function.prototype.bind, aE = !1, oE = null, sE = null, cE = null, lE = null, uE = null, dE = null, fE = null, pE = null, mE = null, hE = null;
		oE = function(e, t, i, a) {
			t = n(e, t), t !== null && (i = r(t.memoizedState, i, 0, a), t.memoizedState = i, t.baseState = i, e.memoizedProps = L({}, e.memoizedProps), i = Fr(e, 2), i !== null && su(i, e, 2));
		}, sE = function(e, t, r) {
			t = n(e, t), t !== null && (r = s(t.memoizedState, r, 0), t.memoizedState = r, t.baseState = r, e.memoizedProps = L({}, e.memoizedProps), r = Fr(e, 2), r !== null && su(r, e, 2));
		}, cE = function(e, t, r, a) {
			t = n(e, t), t !== null && (r = i(t.memoizedState, r, a), t.memoizedState = r, t.baseState = r, e.memoizedProps = L({}, e.memoizedProps), r = Fr(e, 2), r !== null && su(r, e, 2));
		}, lE = function(e, t, n) {
			e.pendingProps = r(e.memoizedProps, t, 0, n), e.alternate && (e.alternate.pendingProps = e.pendingProps), t = Fr(e, 2), t !== null && su(t, e, 2);
		}, uE = function(e, t) {
			e.pendingProps = s(e.memoizedProps, t, 0), e.alternate && (e.alternate.pendingProps = e.pendingProps), t = Fr(e, 2), t !== null && su(t, e, 2);
		}, dE = function(e, t, n) {
			e.pendingProps = i(e.memoizedProps, t, n), e.alternate && (e.alternate.pendingProps = e.pendingProps), t = Fr(e, 2), t !== null && su(t, e, 2);
		}, fE = function(e) {
			var t = Fr(e, 2);
			t !== null && su(t, e, 2);
		}, pE = function(e) {
			var t = $e(), n = Fr(e, t);
			n !== null && su(n, e, t);
		}, mE = function(e) {
			u = e;
		}, hE = function(e) {
			c = e;
		};
		var gE = !0, _E = null, vE = !1, yE = null, bE = null, xE = null, SE = /* @__PURE__ */ new Map(), CE = /* @__PURE__ */ new Map(), wE = [], TE = "mousedown mouseup touchcancel touchend touchstart auxclick dblclick pointercancel pointerdown pointerup dragend dragstart drop compositionend compositionstart keydown keypress keyup input textInput copy cut paste click change contextmenu reset".split(" "), EE = null;
		if (xm.prototype.render = bm.prototype.render = function(e) {
			var t = this._internalRoot;
			if (t === null) throw Error("Cannot update an unmounted root.");
			var n = arguments;
			typeof n[1] == "function" ? console.error("does not support the second callback argument. To execute a side effect after rendering, declare it in a component body with useEffect().") : b(n[1]) ? console.error("You passed a container to the second argument of root.render(...). You don't need to pass it again since you already passed it to create the root.") : n[1] !== void 0 && console.error("You passed a second argument to root.render(...) but it only accepts one argument."), n = e;
			var r = t.current;
			Zp(r, iu(r), n, t, null, null);
		}, xm.prototype.unmount = bm.prototype.unmount = function() {
			var e = arguments;
			if (typeof e[0] == "function" && console.error("does not support a callback argument. To execute a side effect after rendering, declare it in a component body with useEffect()."), e = this._internalRoot, e !== null) {
				this._internalRoot = null;
				var t = e.containerInfo;
				(Z & (iC | aC)) !== rC && console.error("Attempted to synchronously unmount a root while React was already rendering. React cannot finish unmounting the root until the current render has completed, which may lead to a race condition."), Zp(e.current, 2, null, e, null, null), fu(), t[Jh] = null;
			}
		}, xm.prototype.unstable_scheduleHydration = function(e) {
			if (e) {
				var t = ut();
				e = {
					blockedOn: null,
					target: e,
					priority: t
				};
				for (var n = 0; n < wE.length && t !== 0 && t < wE[n].priority; n++);
				wE.splice(n, 0, e), n === 0 && fm(e);
			}
		}, (function() {
			var e = wm.version;
			if (e !== "19.3.1") throw Error("Incompatible React versions: The \"react\" and \"react-dom\" packages must have the exact same version. Instead got:\n  - react:      " + (e + "\n  - react-dom:  19.3.1\nLearn more: https://react.dev/warnings/version-mismatch"));
		})(), typeof Map == "function" && Map.prototype != null && typeof Map.prototype.forEach == "function" && typeof Set == "function" && Set.prototype != null && typeof Set.prototype.clear == "function" && typeof Set.prototype.forEach == "function" || console.error("React depends on Map and Set built-in types. Make sure that you load a polyfill in older browsers. https://react.dev/link/react-polyfills"), z.findDOMNode = function(e) {
			var t = e._reactInternals;
			if (t === void 0) throw typeof e.render == "function" ? Error("Unable to find node on an unmounted component.") : (e = Object.keys(e).join(","), Error("Argument appears to not be a ReactComponent. Keys: " + e));
			return e = ie(t), e = e === null ? null : ae(e), e = e === null ? null : e.stateNode, e;
		}, !(function() {
			var e = {
				bundleType: 1,
				version: "19.3.1",
				rendererPackageName: "react-dom",
				currentDispatcherRef: R,
				reconcilerVersion: "19.3.1"
			};
			return e.overrideHookState = oE, e.overrideHookStateDeletePath = sE, e.overrideHookStateRenamePath = cE, e.overrideProps = lE, e.overridePropsDeletePath = uE, e.overridePropsRenamePath = dE, e.scheduleUpdate = fE, e.scheduleRetry = pE, e.setErrorHandler = mE, e.setSuspenseHandler = hE, e.scheduleRefresh = v, e.scheduleRoot = _, e.setRefreshHandler = y, e.getCurrentFiber = nm, Ge(e);
		})() && t_ && window.top === window.self && (-1 < navigator.userAgent.indexOf("Chrome") && navigator.userAgent.indexOf("Edge") === -1 || -1 < navigator.userAgent.indexOf("Firefox"))) {
			var DE = window.location.protocol;
			/^(https?|file):$/.test(DE) && console.info("%cDownload the React DevTools for a better development experience: https://react.dev/link/react-devtools" + (DE === "file:" ? "\nYou might need to use a local HTTP server (instead of file://): https://react.dev/link/react-devtools-faq" : ""), "font-weight:bold");
		}
		e.createRoot = function(e, t) {
			if (!b(e)) throw Error("Target container is not a DOM element.");
			Sm(e);
			var n = !1, r = "", i = Os, a = ks, o = As;
			return t != null && (t.hydrate ? console.warn("hydrate through createRoot is deprecated. Use ReactDOMClient.hydrateRoot(container, <App />) instead.") : typeof t == "object" && t && t.$$typeof === km && console.error("You passed a JSX element to createRoot. You probably meant to call root.render instead. Example usage:\n\n  let root = createRoot(domContainer);\n  root.render(<App />);"), !0 === t.unstable_strictMode && (n = !0), t.identifierPrefix !== void 0 && (r = t.identifierPrefix), t.onUncaughtError !== void 0 && (i = t.onUncaughtError), t.onCaughtError !== void 0 && (a = t.onCaughtError), t.onRecoverableError !== void 0 && (o = t.onRecoverableError)), t = Yp(e, 1, !1, null, null, n, r, null, i, a, o, ym), e[Jh] = t.current, vd(e), new bm(t);
		}, e.hydrateRoot = function(e, t, n) {
			if (!b(e)) throw Error("Target container is not a DOM element.");
			Sm(e), t === void 0 && console.error("Must provide initial children as second argument to hydrateRoot. Example usage: hydrateRoot(domContainer, <App />)");
			var r = !1, i = "", a = Os, o = ks, s = As, c = null;
			return n != null && (!0 === n.unstable_strictMode && (r = !0), n.identifierPrefix !== void 0 && (i = n.identifierPrefix), n.onUncaughtError !== void 0 && (a = n.onUncaughtError), n.onCaughtError !== void 0 && (o = n.onCaughtError), n.onRecoverableError !== void 0 && (s = n.onRecoverableError), n.formState !== void 0 && (c = n.formState)), t = Yp(e, 1, !0, t, n ?? null, r, i, c, a, o, s, ym), t.context = Xp(null), n = t.current, r = iu(n), r = ot(r), i = Ta(r), i.callback = null, Ea(n, i, r), Ni(r, "hydrateRoot()", null), n = r, t.current.lanes = n, tt(t, n), ad(t), e[Jh] = t.current, vd(e), new xm(t);
		}, e.version = "19.3.1", typeof __REACT_DEVTOOLS_GLOBAL_HOOK__ < "u" && typeof __REACT_DEVTOOLS_GLOBAL_HOOK__.registerInternalModuleStop == "function" && __REACT_DEVTOOLS_GLOBAL_HOOK__.registerInternalModuleStop(Error());
	})();
})), f = (/* @__PURE__ */ n(((e, t) => {
	function n() {
		if (typeof __REACT_DEVTOOLS_GLOBAL_HOOK__ < "u" && typeof __REACT_DEVTOOLS_GLOBAL_HOOK__.checkDCE == "function") {
			if (process.env.NODE_ENV !== "production") throw Error("^_^");
			try {
				__REACT_DEVTOOLS_GLOBAL_HOOK__.checkDCE(n);
			} catch (e) {
				console.error(e);
			}
		}
	}
	process.env.NODE_ENV === "production" ? (n(), t.exports = u()) : t.exports = d();
})))(), p = /* @__PURE__ */ e(t()), m = r();
function h({ slides: e, startIndex: t = 0, asset: n, onOpen: r }) {
	let [i, a] = (0, p.useState)(t), o = (0, p.useRef)(null), s = (0, p.useRef)(!1), c = (0, p.useRef)(null), l = window.CM?.state?.preferences?.motion !== !1 && !window.matchMedia("(prefers-reduced-motion: reduce)").matches, u = e.length;
	if ((0, p.useEffect)(() => {
		a((e) => u ? (e % u + u) % u : 0);
	}, [u]), (0, p.useLayoutEffect)(() => {
		if (!u) return;
		window.CM72 && (window.CM72.galleryIndex = i);
		let e = document.querySelectorAll(".cm-react-carousel-slide");
		l && e.length && window.CMSiteMotion?.carousel?.(e, i, !0);
	}, [
		i,
		l,
		u
	]), !u) return null;
	let d = (e) => a((t) => (t + e + u) % u);
	return /* @__PURE__ */ (0, m.jsxs)("section", {
		id: "cm72-carousel",
		className: "cm72-gallery cm72-reveal",
		role: "region",
		"aria-roledescription": "карусель",
		"aria-label": "Скриншоты CopiMine",
		tabIndex: 0,
		onKeyDown: (e) => {
			e.key === "ArrowRight" && (e.preventDefault(), d(1)), e.key === "ArrowLeft" && (e.preventDefault(), d(-1));
		},
		children: [/* @__PURE__ */ (0, m.jsxs)("div", {
			className: "cm72-section-heading",
			children: [/* @__PURE__ */ (0, m.jsx)("div", { children: /* @__PURE__ */ (0, m.jsx)("h2", { children: "Скриншоты CopiMine" }) }), /* @__PURE__ */ (0, m.jsxs)("div", {
				className: "cm72-gallery-controls",
				children: [/* @__PURE__ */ (0, m.jsxs)("span", {
					"aria-live": "polite",
					children: [
						String(i + 1).padStart(2, "0"),
						" ",
						/* @__PURE__ */ (0, m.jsxs)("i", { children: ["/ ", String(u).padStart(2, "0")] })
					]
				}), u > 1 && /* @__PURE__ */ (0, m.jsxs)(m.Fragment, { children: [/* @__PURE__ */ (0, m.jsx)("button", {
					className: "cm72-round",
					type: "button",
					"aria-label": "Предыдущий кадр",
					onClick: () => d(-1),
					children: /* @__PURE__ */ (0, m.jsx)("svg", {
						className: "icon",
						viewBox: "0 0 24 24",
						fill: "none",
						stroke: "currentColor",
						strokeWidth: "1.8",
						strokeLinecap: "round",
						strokeLinejoin: "round",
						"aria-hidden": "true",
						children: /* @__PURE__ */ (0, m.jsx)("path", { d: "m14.5 5-7 7 7 7" })
					})
				}), /* @__PURE__ */ (0, m.jsx)("button", {
					className: "cm72-round",
					type: "button",
					"aria-label": "Следующий кадр",
					onClick: () => d(1),
					children: /* @__PURE__ */ (0, m.jsx)("svg", {
						className: "icon",
						viewBox: "0 0 24 24",
						fill: "none",
						stroke: "currentColor",
						strokeWidth: "1.8",
						strokeLinecap: "round",
						strokeLinejoin: "round",
						"aria-hidden": "true",
						children: /* @__PURE__ */ (0, m.jsx)("path", { d: "m9.5 5 7 7-7 7" })
					})
				})] })]
			})]
		}), /* @__PURE__ */ (0, m.jsx)("div", {
			className: "cm72-gallery-stage" + (s.current ? " is-dragging" : ""),
			id: "cm72-gallery-stage",
			ref: c,
			"aria-label": "Перетаскивай кадры мышью или пальцем",
			onPointerDown: (e) => {
				e.button === 0 && (o.current = e.clientX, s.current = !1, e.currentTarget.setPointerCapture(e.pointerId));
			},
			onPointerMove: (e) => {
				if (o.current === null) return;
				let t = e.clientX - o.current;
				Math.abs(t) > 7 && (s.current = !0), s.current && c.current && window.CMSiteMotion?.drag?.(c.current, t, l);
			},
			onPointerUp: (e) => {
				if (o.current === null) return;
				let t = e.clientX - o.current;
				o.current = null, Math.abs(t) > 48 ? (d(t < 0 ? 1 : -1), s.current = !0, window.setTimeout(() => {
					s.current = !1;
				}, 0)) : s.current = !1, c.current && window.CMSiteMotion?.settle?.(c.current, l);
			},
			onPointerCancel: () => {
				o.current = null, s.current = !1, c.current && window.CMSiteMotion?.settle?.(c.current, l);
			},
			children: /* @__PURE__ */ (0, m.jsx)("div", {
				className: "cm72-gallery-track",
				children: e.map((e, t) => {
					let a = t - i;
					a > u / 2 && (a -= u), a < -u / 2 && (a += u);
					let o = Math.abs(a), c = o <= 1, l = "translate(-50%, -50%) translateX(" + a * 200 + "px) translateZ(" + -o * 180 + "px) rotateY(" + -a * 11 + "deg) rotateZ(" + a * 1.4 + "deg) scale(" + Math.max(.52, 1 - o * .14) + ")";
					return /* @__PURE__ */ (0, m.jsxs)("button", {
						className: "cm72-carousel-slide cm-react-carousel-slide",
						type: "button",
						style: {
							transform: l,
							opacity: c ? Math.max(.25, 1 - o * .28) : 0,
							zIndex: 5 - o
						},
						"data-index": t,
						"data-position": a,
						"aria-label": (e.title || "Кадр") + ". " + (e.caption || ""),
						"aria-current": t === i,
						"aria-hidden": !c,
						tabIndex: c ? 0 : -1,
						onClick: () => {
							s.current ? s.current = !1 : t === i ? r(e, t) : d(t - i);
						},
						children: [/* @__PURE__ */ (0, m.jsx)("img", {
							src: n(e.src || ""),
							alt: e.alt || e.title || "",
							loading: "lazy",
							decoding: "async",
							draggable: !1
						}), /* @__PURE__ */ (0, m.jsxs)("span", {
							className: "cm72-carousel-caption",
							children: [/* @__PURE__ */ (0, m.jsx)("strong", { children: e.title }), /* @__PURE__ */ (0, m.jsx)("small", { children: e.caption })]
						})]
					}, e.id || e.src || t);
				})
			})
		})]
	});
}
// src/rising-lines.tsx
var g = (e) => {
	let t = e >>> 0;
	return () => {
		t = t + 1831565813 >>> 0;
		let e = t;
		return e = Math.imul(e ^ e >>> 15, e | 1), e ^= e + Math.imul(e ^ e >>> 7, e | 61), ((e ^ e >>> 14) >>> 0) / 4294967296;
	};
};
function _({ className: e = "", particles: t = 72, color: n = "#38dda4", opacity: r = .17 }) {
	let i = (0, p.useRef)(null), a = (0, p.useRef)(null);
	return (0, p.useEffect)(() => {
		let e = i.current, o = a.current, s = o?.getContext("2d", { alpha: !0 });
		if (!e || !o || !s) return;
		let c = 0, l = 0, u = 1, d = 1, f = 1, p = g(12648430), m = Math.min(180, Math.max(0, t)), h = [], _ = n.match(/[0-9a-f]{2}/gi)?.map((e) => parseInt(e, 16)) ?? [
			56,
			221,
			164
		], v = _[0] + "," + _[1] + "," + _[2], y = window.matchMedia("(prefers-reduced-motion: reduce)").matches || window.CM?.state?.preferences?.motion === !1, b = () => {
			let t = e.getBoundingClientRect();
			u = Math.max(1, Math.floor(t.width)), d = Math.max(1, Math.floor(t.height)), f = Math.min(window.devicePixelRatio || 1, 2), o.width = Math.floor(u * f), o.height = Math.floor(d * f), o.style.width = u + "px", o.style.height = d + "px", s.setTransform(f, 0, 0, f, 0, 0), h = Array.from({ length: m }, () => ({
				x: p() * u,
				y: p() * d,
				speed: 6 + p() * 17,
				length: 10 + p() ** .7 * 34,
				alpha: .15 + p() * .55
			})), y && ee(0);
		}, ee = (e) => {
			s.clearRect(0, 0, u, d), s.globalCompositeOperation = "lighter";
			let t = Math.min(.04, Math.max(0, e));
			for (let e of h) {
				y || (e.y -= e.speed * t), !y && e.y + e.length < 0 && (e.x = p() * u, e.y = d + p() * 24, e.speed = 6 + p() * 17, e.length = 10 + p() ** .7 * 34, e.alpha = .15 + p() * .55);
				let n = 1 - e.y / d, i = Math.max(0, Math.min(1, Math.min(n * 3, (1 - n) * 2.2))), a = s.createLinearGradient(e.x, e.y, e.x, e.y + e.length);
				a.addColorStop(0, "rgba(" + v + ",0)"), a.addColorStop(.72, "rgba(" + v + "," + r * e.alpha * i + ")"), a.addColorStop(1, "rgba(" + v + "," + r * e.alpha * i + ")"), s.fillStyle = a, s.fillRect(Math.floor(e.x), Math.floor(e.y), 1, e.length);
			}
			s.globalCompositeOperation = "source-over";
		}, te = (e) => {
			let t = l ? (e - l) / 1e3 : 0;
			l = e, ee(t), y || (c = requestAnimationFrame(te));
		}, ne = new ResizeObserver(b);
		return ne.observe(e), b(), y || (c = requestAnimationFrame(te)), () => {
			ne.disconnect(), cancelAnimationFrame(c), s.clearRect(0, 0, u, d);
		};
	}, [
		n,
		r,
		t
	]), /* @__PURE__ */ (0, m.jsx)("div", {
		ref: i,
		className: "cm-react-rising-lines " + e,
		"aria-hidden": "true",
		children: /* @__PURE__ */ (0, m.jsx)("canvas", { ref: a })
	});
}
// src/app.tsx
var v = (e, t = "") => {
	let n = window.icon?.(e, t) || "";
	return /* @__PURE__ */ (0, m.jsx)("span", {
		className: t,
		"aria-hidden": "true",
		dangerouslySetInnerHTML: { __html: n }
	});
};
function y() {
	let e = (window.CM?.state || {}).config || {}, t = String(e.headline || "CopiMine —\nMinecraft-сервер.").split(/\r?\n/), n = t.shift()?.trim() || "CopiMine", r = t.join(" ").trim(), i = e.serverIp || "copimine.ru";
	return /* @__PURE__ */ (0, m.jsxs)("section", {
		className: "cm72-hero cm-react-hero",
		children: [
			/* @__PURE__ */ (0, m.jsx)("img", {
				className: "cm72-hero-photo",
				src: window.CM.asset("assets/photos/copimine-night.webp"),
				alt: "",
				fetchPriority: "high"
			}),
			/* @__PURE__ */ (0, m.jsx)(_, {
				className: "cm-react-hero-lines",
				particles: 74,
				color: "#5ce5b2",
				opacity: .14
			}),
			/* @__PURE__ */ (0, m.jsxs)("div", {
				className: "cm72-hero-content",
				children: [
					/* @__PURE__ */ (0, m.jsxs)("h1", {
						className: "cm-react-hero-title",
						children: [n, r && /* @__PURE__ */ (0, m.jsxs)(m.Fragment, { children: [/* @__PURE__ */ (0, m.jsx)("br", {}), /* @__PURE__ */ (0, m.jsx)("span", { children: r })] })]
					}),
					/* @__PURE__ */ (0, m.jsxs)("div", {
						className: "cm-react-hero-actions",
						children: [/* @__PURE__ */ (0, m.jsxs)("button", {
							className: "btn primary cm72-start",
							type: "button",
							"data-action": "play",
							children: ["Начать играть ", v("arrow-right")]
						}), /* @__PURE__ */ (0, m.jsxs)("a", {
							className: "btn ghost",
							href: "#map",
							children: [v("map"), "Посмотреть карту"]
						})]
					}),
					/* @__PURE__ */ (0, m.jsxs)("button", {
						className: "cm72-address",
						type: "button",
						"data-action": "copy-ip",
						"aria-label": "Скопировать адрес сервера " + i,
						children: [/* @__PURE__ */ (0, m.jsx)("span", { children: i }), v("copy")]
					})
				]
			})
		]
	});
}
function b({ product: e }) {
	let t = window.CM;
	return /* @__PURE__ */ (0, m.jsxs)("article", {
		className: "product reveal",
		children: [/* @__PURE__ */ (0, m.jsxs)("div", {
			className: "product-image",
			children: [
				/* @__PURE__ */ (0, m.jsx)("span", {
					className: "tag",
					children: e.category
				}),
				/* @__PURE__ */ (0, m.jsx)("img", {
					src: t.asset(e.image),
					alt: e.name,
					width: 86,
					height: 86,
					loading: "lazy"
				}),
				/* @__PURE__ */ (0, m.jsx)("button", {
					className: "icon-btn",
					type: "button",
					"data-action": "product",
					"data-id": e.id,
					"aria-label": "Подробнее: " + e.name,
					children: v("arrow-up-right")
				})
			]
		}), /* @__PURE__ */ (0, m.jsxs)("div", {
			className: "product-body",
			children: [
				/* @__PURE__ */ (0, m.jsx)("h3", { children: /* @__PURE__ */ (0, m.jsx)("button", {
					type: "button",
					"data-action": "product",
					"data-id": e.id,
					children: e.name
				}) }),
				/* @__PURE__ */ (0, m.jsx)("p", { children: e.description }),
				/* @__PURE__ */ (0, m.jsxs)("div", {
					className: "product-bottom",
					children: [/* @__PURE__ */ (0, m.jsxs)("span", {
						className: "price",
						children: [
							Number(e.price).toLocaleString("ru-RU"),
							" ",
							/* @__PURE__ */ (0, m.jsx)("small", { children: e.currency })
						]
					}), /* @__PURE__ */ (0, m.jsxs)("button", {
						className: "btn",
						type: "button",
						"data-action": "add-cart",
						"data-id": e.id,
						children: [v("plus"), "В корзину"]
					})]
				})
			]
		})]
	});
}
function ee() {
	return /* @__PURE__ */ (0, m.jsxs)("a", {
		href: "#map",
		className: "cm72-map-image cm72-map-preview",
		"aria-label": "Открыть карту миров CopiMine",
		children: [/* @__PURE__ */ (0, m.jsx)("img", {
			src: window.CM.asset("assets/img/map-world.webp"),
			alt: "",
			loading: "lazy",
			decoding: "async"
		}), /* @__PURE__ */ (0, m.jsxs)("div", {
			className: "cm72-map-preview-content",
			children: [/* @__PURE__ */ (0, m.jsxs)("div", {
				className: "cm72-map-preview-brand",
				children: [/* @__PURE__ */ (0, m.jsx)("img", {
					src: window.CM.asset("assets/img/brands/bluemap.png"),
					alt: "BlueMap",
					loading: "lazy"
				}), /* @__PURE__ */ (0, m.jsx)("strong", { children: "Карта миров" })]
			}), /* @__PURE__ */ (0, m.jsxs)("span", {
				className: "cm72-map-preview-link",
				children: ["Открыть ", v("arrow-up-right")]
			})]
		})]
	});
}
function te() {
	return (window.CM?.state?.gallery || []).filter((e) => e.published && e.kind !== "map" && !String(e.src || "").includes("/map/")).sort((e, t) => (e.position || 0) - (t.position || 0));
}
function ne() {
	let e = window.CM, t = e.state, n = te(), r = [
		"return_stone",
		"infinite_torch",
		"pohuy_na_debaffy_amulet",
		"gde_moy_lut_blyat_compass"
	].map((t) => e.item(t)).filter((e) => e?.enabled), i = (t.faq || []).filter((e) => e.published);
	return /* @__PURE__ */ (0, m.jsxs)(m.Fragment, { children: [
		/* @__PURE__ */ (0, m.jsx)(y, {}),
		/* @__PURE__ */ (0, m.jsx)(h, {
			slides: n,
			startIndex: window.CM72?.galleryIndex || 0,
			asset: e.asset,
			onOpen: (t) => {
				e.openModal(e.esc(t.title || "Скриншот"), "<div class=\"cm72-lightbox\"><img src=\"" + e.asset(t.src || "") + "\" alt=\"" + e.esc(t.alt || t.title || "") + "\"><div class=\"cm72-lightbox-bottom\"><p>" + e.esc(t.caption || "") + "</p></div></div>", "cm72-lightbox-dialog");
			}
		}),
		/* @__PURE__ */ (0, m.jsxs)("section", {
			className: "cm72-shop-section cm72-reveal cm-react-shop-section",
			children: [/* @__PURE__ */ (0, m.jsxs)("div", {
				className: "cm72-section-heading",
				children: [/* @__PURE__ */ (0, m.jsx)("div", { children: /* @__PURE__ */ (0, m.jsx)("h2", { children: "Магазин" }) }), /* @__PURE__ */ (0, m.jsxs)("a", {
					className: "text-link",
					href: "#shop",
					children: ["Весь каталог ", v("arrow-right")]
				})]
			}), /* @__PURE__ */ (0, m.jsx)("div", {
				className: "product-grid four",
				children: r.map((e) => /* @__PURE__ */ (0, m.jsx)(b, { product: e }, e.id))
			})]
		}),
		/* @__PURE__ */ (0, m.jsx)("section", {
			className: "cm72-map-section cm72-reveal cm-react-map-section",
			children: /* @__PURE__ */ (0, m.jsx)(ee, {})
		}),
		i.length > 0 && /* @__PURE__ */ (0, m.jsxs)("section", {
			className: "cm72-faq cm72-reveal",
			children: [/* @__PURE__ */ (0, m.jsxs)("div", { children: [/* @__PURE__ */ (0, m.jsx)("h2", { children: "FAQ" }), /* @__PURE__ */ (0, m.jsxs)("a", {
				className: "text-link",
				href: "#faq",
				children: ["Все вопросы ", v("arrow-right")]
			})] }), /* @__PURE__ */ (0, m.jsx)("div", {
				className: "faq-list",
				children: i.map((e) => /* @__PURE__ */ (0, m.jsxs)("details", {
					name: "cm72-faq",
					children: [/* @__PURE__ */ (0, m.jsxs)("summary", { children: [e.question, v("plus")] }), /* @__PURE__ */ (0, m.jsx)("p", { children: e.answer })]
				}, e.id || e.question))
			})]
		})
	] });
}
function re() {
	let e = window.CMBlueMap?.state?.() || {}, t = [
		["overworld", "Обычный мир"],
		["nether", "Незер"],
		["end", "Энд"]
	], n = e.dimension || "overworld";
	return /* @__PURE__ */ (0, m.jsxs)("section", {
		className: "cm-react-map-page",
		"aria-labelledby": "map-title",
		children: [
			/* @__PURE__ */ (0, m.jsxs)("header", {
				className: "cm-react-map-header",
				children: [/* @__PURE__ */ (0, m.jsx)("h1", {
					id: "map-title",
					children: "Карта"
				}), /* @__PURE__ */ (0, m.jsxs)("div", {
					className: "cm-react-map-service",
					"aria-label": "BlueMap",
					children: [/* @__PURE__ */ (0, m.jsx)("img", {
						src: window.CM.asset("assets/img/brands/bluemap.png"),
						alt: "BlueMap"
					}), /* @__PURE__ */ (0, m.jsx)("span", { children: "BlueMap" })]
				})]
			}),
			/* @__PURE__ */ (0, m.jsxs)("div", {
				className: "cm-react-map-toolbar",
				role: "toolbar",
				"aria-label": "Управление картой",
				children: [/* @__PURE__ */ (0, m.jsx)("div", {
					className: "cm-react-map-worlds",
					role: "group",
					"aria-label": "Измерение",
					children: t.map(([e, t]) => /* @__PURE__ */ (0, m.jsx)("button", {
						type: "button",
						"data-action": "production-map-dimension",
						"data-dimension": e,
						"aria-pressed": n === e,
						children: t
					}, e))
				}), /* @__PURE__ */ (0, m.jsxs)("button", {
					className: "cm-react-map-fullscreen",
					type: "button",
					"data-action": "v6-map-fullscreen",
					"aria-label": "Открыть карту на весь экран",
					children: ["На весь экран ", v("expand")]
				})]
			}),
			/* @__PURE__ */ (0, m.jsx)("div", {
				className: "cm-react-map-stage production-map-stage",
				id: "production-map-stage",
				children: e.viewerUrl ? /* @__PURE__ */ (0, m.jsx)("iframe", {
					className: "production-map-frame",
					id: "production-map-frame",
					src: e.viewerUrl,
					title: `BlueMap — ${t.find(([e]) => e === n)?.[1] || "карта"}`,
					allow: "fullscreen",
					referrerPolicy: "same-origin"
				}) : /* @__PURE__ */ (0, m.jsxs)("div", {
					className: "cm-react-map-empty",
					role: e.error ? "alert" : "status",
					children: [
						/* @__PURE__ */ (0, m.jsx)("img", {
							src: window.CM.asset("assets/img/brands/bluemap.png"),
							alt: ""
						}),
						/* @__PURE__ */ (0, m.jsx)("p", { children: e.error ? "Карта сейчас недоступна." : e.loaded ? "Карта ещё не опубликована." : "Загрузка карты…" }),
						e.error && /* @__PURE__ */ (0, m.jsx)("button", {
							className: "btn",
							type: "button",
							"data-action": "production-map-retry",
							children: "Повторить"
						})
					]
				})
			})
		]
	});
}
function ie({ html: e }) {
	let t = (window.CM?.state?.pages || []).find((e) => e?.slug === "rules" && e?.published)?.published;
	return !t || !window.CMDomain?.markdown ? /* @__PURE__ */ (0, m.jsx)(x, { html: e }) : /* @__PURE__ */ (0, m.jsxs)("article", {
		className: "cm-react-rules",
		children: [
			/* @__PURE__ */ (0, m.jsx)("h1", { children: t.title || "Правила" }),
			/* @__PURE__ */ (0, m.jsx)("div", {
				className: "prose",
				dangerouslySetInnerHTML: { __html: window.CMDomain.markdown(t.body || "") }
			}),
			/* @__PURE__ */ (0, m.jsxs)("a", {
				className: "cm-react-rules-support",
				href: "#faq",
				children: ["Вопросы и поддержка ", v("arrow-right")]
			})
		]
	});
}
function ae() {
	let { user: e, pending: t } = window.CMAccountLink?.state?.() || {};
	return e ? e.gameLinkVerified ? /* @__PURE__ */ (0, m.jsxs)("section", {
		className: "cm-link-flow cm-link-complete",
		children: [
			/* @__PURE__ */ (0, m.jsx)("p", { children: "Привязан к аккаунту Minecraft" }),
			/* @__PURE__ */ (0, m.jsx)("h1", { children: e.minecraftName || e.name }),
			/* @__PURE__ */ (0, m.jsxs)("a", {
				className: "btn primary",
				href: "#account",
				children: ["Открыть кабинет ", v("arrow-right")]
			})
		]
	}) : /* @__PURE__ */ (0, m.jsxs)("section", {
		className: "cm-link-flow",
		"aria-labelledby": "cm-link-title",
		children: [
			/* @__PURE__ */ (0, m.jsxs)("div", {
				className: "cm-link-progress",
				"aria-label": t ? "Шаг 2 из 2: код" : "Шаг 1 из 2: ник",
				children: [
					/* @__PURE__ */ (0, m.jsxs)("span", {
						className: "is-done",
						children: [/* @__PURE__ */ (0, m.jsx)("b", { children: "01" }), /* @__PURE__ */ (0, m.jsx)("small", { children: "Ник" })]
					}),
					/* @__PURE__ */ (0, m.jsx)("i", {}),
					/* @__PURE__ */ (0, m.jsxs)("span", {
						className: t ? "is-current" : "",
						children: [/* @__PURE__ */ (0, m.jsx)("b", { children: "02" }), /* @__PURE__ */ (0, m.jsx)("small", { children: "Код" })]
					})
				]
			}),
			/* @__PURE__ */ (0, m.jsx)("span", {
				className: "kicker",
				children: "Привязка аккаунта"
			}),
			/* @__PURE__ */ (0, m.jsx)("h1", {
				id: "cm-link-title",
				children: t ? "Подтверди ник в игре" : "Укажи ник Minecraft"
			}),
			t ? /* @__PURE__ */ (0, m.jsxs)(m.Fragment, { children: [
				/* @__PURE__ */ (0, m.jsxs)("ol", {
					className: "cm-link-steps",
					children: [/* @__PURE__ */ (0, m.jsxs)("li", { children: [
						"Войди на сервер под ником ",
						/* @__PURE__ */ (0, m.jsx)("strong", { children: t.name }),
						"."
					] }), /* @__PURE__ */ (0, m.jsx)("li", { children: "Открой личное сообщение от CopiMine и введи код ниже." })]
				}),
				/* @__PURE__ */ (0, m.jsxs)("form", {
					"data-form": "production-mc-confirm",
					className: "cm-link-form",
					children: [
						/* @__PURE__ */ (0, m.jsxs)("label", {
							className: "field",
							children: ["Одноразовый код", /* @__PURE__ */ (0, m.jsx)("input", {
								name: "code",
								type: "text",
								required: !0,
								minLength: 6,
								maxLength: 12,
								autoComplete: "one-time-code",
								autoCapitalize: "characters"
							})]
						}),
						/* @__PURE__ */ (0, m.jsx)("div", {
							className: "form-error",
							role: "alert"
						}),
						/* @__PURE__ */ (0, m.jsxs)("button", {
							className: "btn primary",
							type: "submit",
							children: ["Подтвердить и открыть кабинет ", v("arrow-right")]
						})
					]
				}),
				/* @__PURE__ */ (0, m.jsxs)("p", {
					className: "cm-link-status",
					"data-link-status": !0,
					role: "status",
					children: [
						"Код действует до ",
						t.expiresAt ? new Date(t.expiresAt).toLocaleTimeString("ru-RU", {
							hour: "2-digit",
							minute: "2-digit"
						}) : "10 минут",
						"."
					]
				}),
				/* @__PURE__ */ (0, m.jsx)("button", {
					className: "cm-link-restart",
					type: "button",
					"data-action": "production-mc-restart",
					children: "Запросить новый код"
				})
			] }) : /* @__PURE__ */ (0, m.jsxs)(m.Fragment, { children: [/* @__PURE__ */ (0, m.jsx)("p", { children: "Укажи ник Minecraft. Привязка подтвердится сообщением в игре." }), /* @__PURE__ */ (0, m.jsxs)("form", {
				"data-form": "production-mc-code",
				className: "cm-link-form",
				children: [
					/* @__PURE__ */ (0, m.jsxs)("label", {
						className: "field",
						children: ["Ник Minecraft", /* @__PURE__ */ (0, m.jsx)("input", {
							name: "name",
							type: "text",
							required: !0,
							pattern: "[A-Za-z0-9_]{3,16}",
							minLength: 3,
							maxLength: 16,
							autoComplete: "off",
							autoCapitalize: "none",
							spellCheck: !1
						})]
					}),
					/* @__PURE__ */ (0, m.jsx)("div", {
						className: "form-error",
						role: "alert"
					}),
					/* @__PURE__ */ (0, m.jsxs)("button", {
						className: "btn primary",
						type: "submit",
						children: ["Продолжить ", v("arrow-right")]
					})
				]
			})] })
		]
	}) : /* @__PURE__ */ (0, m.jsxs)("section", {
		className: "cm-link-flow",
		children: [/* @__PURE__ */ (0, m.jsx)("h1", { children: "Войди в аккаунт" }), /* @__PURE__ */ (0, m.jsx)("a", {
			className: "btn primary",
			href: "#auth/login",
			children: "Войти"
		})]
	});
}
function x({ html: e }) {
	return /* @__PURE__ */ (0, m.jsx)("div", { dangerouslySetInnerHTML: { __html: e } });
}
function oe(e) {
	document.getElementById("app")?.removeAttribute("aria-busy"), window.CM?.mountRoute?.(e.page, e.tab), document.querySelector(".header-actions button[aria-label='Открыть меню']")?.classList.add("mobile-nav-toggle");
	let t = document.querySelector(".v71-admin-nav");
	if (t && e.page === "admin" && (t.scrollTop = e.sideScroll), e.same) {
		let t = e.focusId ? document.getElementById(e.focusId) : null;
		if (t?.focus({ preventScroll: !0 }), t && typeof e.focusStart == "number") try {
			t.setSelectionRange?.(e.focusStart, e.focusStart);
		} catch {}
	} else window.scrollTo({
		top: 0,
		behavior: "instant"
	});
}
function se(e) {
	let t = document.getElementById("cm-react-analytics-charts");
	if (t && e.analytics) {
		let n = e.analytics;
		import("./charts-CLdCOh5a.mjs").then(({ AdminAnalyticsCharts: e }) => {
			if (!t.isConnected || document.getElementById(t.id) !== t) return;
			let r = (0, f.createRoot)(t);
			r.render(/* @__PURE__ */ (0, m.jsx)(e, { model: n })), de.push(r);
		}).catch(() => {
			t.isConnected && (t.textContent = "Графики аналитики не загрузились.");
		});
		return;
	}
	let n = document.getElementById("cm-react-live-chart");
	if (n) {
		let t = {
			data: { live: { samples: e.serviceMetrics || [] } },
			sub: "live"
		};
		import("./charts-CLdCOh5a.mjs").then(({ AdminAnalyticsCharts: e }) => {
			if (!n.isConnected || document.getElementById(n.id) !== n) return;
			let r = (0, f.createRoot)(n);
			r.render(/* @__PURE__ */ (0, m.jsx)(e, { model: t })), de.push(r);
		}).catch(() => {
			n.isConnected && (n.textContent = "Графики аналитики не загрузились.");
		});
	}
}
function ce({ model: e }) {
	let t = e.page === "account" && (e.tab === "link" || !!window.CM?.me?.() && !window.CMAccountLink?.state?.()?.user?.gameLinkVerified);
	return (0, p.useLayoutEffect)(() => {
		oe(e), se(e);
	}, [e.revision]), /* @__PURE__ */ (0, m.jsxs)(m.Fragment, { children: [/* @__PURE__ */ (0, m.jsx)("div", {
		id: "persistent-header",
		dangerouslySetInnerHTML: { __html: e.header }
	}), /* @__PURE__ */ (0, m.jsx)("div", {
		id: "page-root",
		children: e.page === "admin" ? /* @__PURE__ */ (0, m.jsx)(x, { html: e.html }) : /* @__PURE__ */ (0, m.jsxs)(m.Fragment, { children: [/* @__PURE__ */ (0, m.jsx)("main", {
			id: "view",
			className: e.page === "map" ? void 0 : t ? "cm-link-route" : "container",
			children: e.page === "home" ? /* @__PURE__ */ (0, m.jsx)(ne, {}) : e.page === "map" ? /* @__PURE__ */ (0, m.jsx)(re, {}) : e.page === "rules" ? /* @__PURE__ */ (0, m.jsx)(ie, { html: e.html }) : t ? /* @__PURE__ */ (0, m.jsx)(ae, {}) : /* @__PURE__ */ (0, m.jsx)(x, { html: e.html })
		}), e.page !== "map" && !t && /* @__PURE__ */ (0, m.jsx)("div", {
			className: "container",
			children: /* @__PURE__ */ (0, m.jsx)("div", { dangerouslySetInnerHTML: { __html: e.footer } })
		})] })
	})] });
}
var le = null, ue = null, de = [];
function fe() {
	let e = document.getElementById("app"), t = document.getElementById("dock-root");
	e && t && !le && (le = (0, f.createRoot)(e), ue = (0, f.createRoot)(t), window.CopimineReactBridge = { render(e) {
		de.forEach((e) => e.unmount()), de = [], e.analytics = window.CMAnalyticsReactModel || null, e.serviceMetrics = window.CM?.state?.serviceMetrics || [], le?.render(/* @__PURE__ */ (0, m.jsx)(ce, { model: e })), ue?.render(/* @__PURE__ */ (0, m.jsx)("div", { dangerouslySetInnerHTML: { __html: e.dock } }));
	} });
}
// src/main.tsx
fe();
