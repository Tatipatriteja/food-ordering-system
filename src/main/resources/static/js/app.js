'use strict';
/* Storefront logic. Shared helpers ($, money, esc, api, toast ...) come from common.js. */

const MAX_QTY = 50;
let foods = [];
let cart = []; // [{ id, qty }]
let activeCategory = 'All';
let searchTerm = '';
let ordersById = new Map();
// Defaults are replaced by the server's real rules (GET /api/billing/rules) as soon as they arrive.
let rules = { taxRate: 0.05, discountThreshold: 500, discountRate: 0.10 };

const dishEmoji = (category) => ({ Pizza: '🍕', Burgers: '🍔', Mains: '🍛', Sides: '🍟', Drinks: '🥤', Desserts: '🍰' }[category] || '🍽️');

/* ---------- loading ---------- */
async function loadRules() {
  try {
    const loaded = await api('api/billing/rules');
    rules = { taxRate: Number(loaded.taxRate), discountThreshold: Number(loaded.discountThreshold), discountRate: Number(loaded.discountRate) };
  } catch (_) { /* keep defaults; the server still calculates the real bill */ }
  $('promoValue').textContent = `${percent(rules.discountRate)} off ${money(rules.discountThreshold).replace('.00', '')}+`;
}

async function loadFoods() {
  $('menu').innerHTML = '<div class="col"><div class="placeholder-glow"><span class="placeholder col-12 rounded-4" style="height:220px"></span></div></div>'.repeat(3);
  try {
    const [response] = await Promise.all([api('api/foods'), loadRules()]);
    foods = Array.isArray(response) ? response : [];
    $('menuCount').textContent = foods.length;
    restoreCart(); renderFilters(); renderMenu(); renderCart();
  } catch (error) {
    $('menu').innerHTML = `<div class="col-12"><div class="alert alert-warning"><i class="bi bi-wifi-off me-2"></i><strong>Menu unavailable.</strong> ${esc(error.message)} <button class="btn btn-sm btn-outline-dark ms-2" id="retryMenu" type="button">Try again</button></div></div>`;
  }
}

/* ---------- menu ---------- */
function renderFilters() {
  const categories = ['All', ...new Set(foods.map((food) => food.category).filter(Boolean))];
  $('filters').innerHTML = categories.map((category) => `<button type="button" class="chip${category === activeCategory ? ' active' : ''}" data-cat="${esc(category)}" aria-pressed="${category === activeCategory}">${esc(category)}</button>`).join('');
}

function renderMenu() {
  const term = searchTerm.trim().toLowerCase();
  const list = foods.filter((food) => (activeCategory === 'All' || food.category === activeCategory)
    && (!term || `${food.name} ${food.description || ''} ${food.category || ''}`.toLowerCase().includes(term)));
  if (!list.length) {
    $('menu').innerHTML = '<div class="col-12"><div class="empty-state"><i class="bi bi-search d-block mb-2"></i><strong>No dishes found</strong><p class="mb-0">Try another search or category.</p></div></div>';
    return;
  }
  $('menu').innerHTML = list.map((food) => `
    <div class="col"><article class="card food-card${food.available ? '' : ' unavailable'}">
      <div class="food-art" aria-hidden="true">${dishEmoji(food.category)}</div>
      <div class="card-body"><span class="food-category">${esc(food.category || 'Food')}</span>
        <h3 class="card-title">${esc(food.name)}</h3><p class="card-text">${esc(food.description || 'Prepared fresh for you.')}</p>
        <div class="d-flex justify-content-between align-items-center gap-2 mt-2"><span class="food-price">${money(food.price)}</span>
        <button type="button" class="btn ${food.available ? 'btn-brand' : 'btn-outline-secondary'} rounded-pill px-3" data-add="${Number(food.id)}" ${food.available ? '' : 'disabled'}>${food.available ? '<i class="bi bi-plus-lg me-1"></i>Add' : 'Unavailable'}</button></div>
      </div></article></div>`).join('');
}

/* ---------- cart ---------- */
function saveCart() { try { localStorage.setItem('cart', JSON.stringify(cart)); } catch (_) { /* storage may be disabled; checkout still works */ } }
function restoreCart() {
  try {
    const saved = JSON.parse(localStorage.getItem('cart') || '[]');
    cart = Array.isArray(saved) ? saved.filter((item) => item && Number.isInteger(item.id) && Number.isInteger(item.qty) && item.qty > 0 && item.qty <= MAX_QTY && foods.some((food) => food.id === item.id && food.available)) : [];
  } catch (_) { cart = []; }
}
function addToCart(id) {
  const food = foods.find((item) => item.id === id && item.available);
  if (!food) return toast('This item is no longer available.', true);
  const found = cart.find((item) => item.id === id);
  if (found) { if (found.qty >= MAX_QTY) return toast(`Maximum quantity is ${MAX_QTY} per item.`, true); found.qty++; }
  else cart.push({ id, qty: 1 });
  renderCart(); toast(`${food.name} added to cart`);
}
function changeQty(id, delta) {
  const item = cart.find((entry) => entry.id === id);
  if (!item) return;
  item.qty = Math.min(MAX_QTY, item.qty + delta);
  if (item.qty <= 0) cart = cart.filter((entry) => entry.id !== id);
  renderCart();
}

/** Bill preview for the cart. The server recalculates and stays authoritative when the order is placed. */
function previewBill(subtotal) {
  const round2 = (n) => Math.round(n * 100) / 100;
  const tax = round2(subtotal * rules.taxRate);
  const discount = subtotal >= rules.discountThreshold ? round2(subtotal * rules.discountRate) : 0;
  return { tax, discount, total: round2(subtotal + tax - discount) };
}

function renderCart() {
  saveCart();
  const count = cart.reduce((sum, item) => sum + item.qty, 0);
  $('cartCount').textContent = count; $('cartStat').textContent = count;
  const box = $('cartItems');
  const validCart = cart.filter((item) => foods.some((food) => food.id === item.id));
  if (!validCart.length) {
    box.innerHTML = '<div class="empty-state"><i class="bi bi-bag d-block mb-2"></i><strong>Your cart is waiting</strong><p class="mb-0">Add something tasty from the menu.</p></div>';
    $('summary').innerHTML = '<div class="empty-state py-3">Your bill summary will appear here.</div>';
    $('placeBtn').disabled = true; return;
  }
  $('placeBtn').disabled = false;
  let subtotal = 0;
  box.innerHTML = validCart.map((item) => {
    const food = foods.find((entry) => entry.id === item.id);
    const lineTotal = Number(food.price) * item.qty; subtotal += lineTotal;
    return `<div class="cart-line"><div class="cart-line-name"><strong>${esc(food.name)}</strong><div class="muted small">${money(food.price)} each</div></div>
      <div class="qty"><button class="btn btn-outline-secondary" type="button" data-dec="${item.id}" aria-label="Decrease ${esc(food.name)} quantity"><i class="bi bi-dash"></i></button><span class="fw-bold">${item.qty}</span><button class="btn btn-outline-secondary" type="button" data-inc="${item.id}" aria-label="Increase ${esc(food.name)} quantity" ${item.qty >= MAX_QTY ? 'disabled' : ''}><i class="bi bi-plus"></i></button></div>
      <strong class="line-price">${money(lineTotal)}</strong><button type="button" class="btn btn-sm btn-link text-danger text-decoration-none" data-del="${item.id}" aria-label="Remove ${esc(food.name)}"><i class="bi bi-trash3"></i></button></div>`;
  }).join('');
  const { tax, discount, total } = previewBill(subtotal);
  const unlocked = subtotal >= rules.discountThreshold;
  $('summary').innerHTML = `<div class="totals"><div><span>Subtotal</span><span>${money(subtotal)}</span></div><div><span>Tax (${percent(rules.taxRate)})</span><span>${money(tax)}</span></div><div><span>Discount${discount ? ` (${percent(rules.discountRate)})` : ''}</span><span>− ${money(discount)}</span></div><div class="grand"><span>Total</span><span>${money(total)}</span></div>${unlocked
    ? `<div class="saving-note"><i class="bi bi-check-circle-fill me-1"></i>${percent(rules.discountRate)} discount unlocked!</div>`
    : `<div class="saving-note"><i class="bi bi-tag-fill me-1"></i>Spend ${money(rules.discountThreshold - subtotal)} more for ${percent(rules.discountRate)} off.</div>`}</div>`;
}
function showCart() { $('cartSection').classList.remove('hidden'); $('ordersSection').classList.add('hidden'); renderCart(); $('cartSection').scrollIntoView({ behavior: 'smooth', block: 'start' }); }

/* ---------- checkout ---------- */
async function placeOrder(event) {
  event.preventDefault();
  const nameField = $('customerName');
  const customerName = nameField.value.trim();
  const customerPhone = $('customerPhone').value.trim();
  nameField.classList.toggle('is-invalid', !customerName);
  if (!cart.length) return toast('Add food to your cart first.', true);
  if (!customerName) { nameField.focus(); return toast('Please enter your name.', true); }
  if (customerPhone && !/^[0-9+\-\s()]{7,20}$/.test(customerPhone)) { $('customerPhone').classList.add('is-invalid'); $('customerPhone').focus(); return toast('Please enter a valid phone number or leave it blank.', true); }
  $('customerPhone').classList.remove('is-invalid');
  const button = $('placeBtn'); const original = button.innerHTML;
  button.disabled = true; button.innerHTML = '<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>Placing order…';
  try {
    const order = await api('api/orders', jsonRequest('POST', { customerName, customerPhone, items: cart.map((item) => ({ foodId: item.id, quantity: item.qty })) }));
    $('bill').innerHTML = billHtml(order); cart = []; renderCart(); $('checkout').reset(); nameField.classList.remove('is-invalid');
    toast(`Order #${order.id} placed successfully!`);
    if (!$('ordersSection').classList.contains('hidden')) loadOrders();
  } catch (error) { toast(error.message, true); }
  finally { button.innerHTML = original; button.disabled = cart.length === 0; }
}

function billLines(order) {
  return (order.items || []).map((item) => `<div class="d-flex justify-content-between gap-3 py-2 border-bottom"><span>${esc(item.foodName)} <span class="text-secondary">× ${Number(item.quantity)}</span></span><strong>${money(item.lineTotal)}</strong></div>`).join('');
}
function billTotals(order, totalLabel) {
  return `<div class="d-flex justify-content-between mt-3"><span>Subtotal</span><span>${money(order.subtotal)}</span></div><div class="d-flex justify-content-between"><span>Tax</span><span>${money(order.tax)}</span></div><div class="d-flex justify-content-between"><span>Discount</span><span>− ${money(order.discount)}</span></div><div class="d-flex justify-content-between align-items-center mt-2"><strong>${totalLabel}</strong><span class="bill-total">${money(order.total)}</span></div>`;
}
function billHtml(order) {
  return `<div class="bill-card"><div class="d-flex align-items-start gap-3"><i class="bi bi-check-circle-fill fs-2"></i><div class="flex-grow-1"><h3>Order #${Number(order.id)} confirmed!</h3><p class="mb-3">Thanks, ${esc(order.customerName)}. Your order has been recorded.</p>${billLines(order)}${billTotals(order, 'Total to pay')}<p class="mb-0 mt-2">Status: ${statusBadge(order.status)}</p></div></div></div>`;
}

/* ---------- order history ---------- */
async function loadOrders() {
  $('ordersSection').classList.remove('hidden'); $('cartSection').classList.add('hidden');
  const box = $('orders'); box.innerHTML = '<div class="text-center py-4"><span class="spinner-border text-secondary" role="status"><span class="visually-hidden">Loading orders...</span></span></div>';
  try {
    const orders = await api('api/orders');
    ordersById = new Map(orders.map((order) => [order.id, order]));
    if (!orders.length) { box.innerHTML = '<div class="empty-state"><i class="bi bi-receipt d-block mb-2"></i><strong>No orders yet</strong><p class="mb-0">Your placed orders will appear here.</p></div>'; }
    else box.innerHTML = `<div class="table-responsive border rounded-4"><table class="table orders-table table-hover mb-0"><thead><tr><th>Order</th><th>Customer</th><th>Date</th><th>Items</th><th>Status</th><th class="text-end">Total</th><th></th></tr></thead><tbody>${orders.map((order) => `<tr><td class="fw-bold">#${Number(order.id)}</td><td>${esc(order.customerName)}</td><td class="text-nowrap">${esc(formatDate(order.orderTime))}</td><td>${(order.items || []).reduce((sum, item) => sum + Number(item.quantity || 0), 0)}</td><td>${statusBadge(order.status)}</td><td class="text-end fw-bold text-nowrap">${money(order.total)}</td><td class="text-end"><button type="button" class="btn btn-sm btn-outline-secondary rounded-pill" data-order="${Number(order.id)}">Details</button></td></tr>`).join('')}</tbody></table></div>`;
    $('ordersSection').scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (error) { box.innerHTML = `<div class="alert alert-warning"><i class="bi bi-exclamation-triangle me-2"></i>Could not load orders: ${esc(error.message)}</div>`; }
}

function showOrder(id) {
  const order = ordersById.get(id);
  if (!order) return;
  $('orderModalTitle').textContent = `Order #${Number(order.id)}`;
  $('orderModalBody').innerHTML = `<p class="mb-1 text-secondary small">${esc(formatDate(order.orderTime))} · ${esc(order.customerName)}</p><p class="mb-2">Status: ${statusBadge(order.status)}</p>${billLines(order)}${billTotals(order, 'Total')}`;
  const cancel = $('cancelOrderBtn');
  const canCancel = order.status === 'PLACED' && (order.nextStatuses || []).includes('CANCELLED');
  cancel.classList.toggle('d-none', !canCancel);
  cancel.dataset.cancel = String(order.id);
  bootstrap.Modal.getOrCreateInstance($('orderModal')).show();
}

async function cancelOrder(id) {
  if (!window.confirm(`Cancel order #${id}? This cannot be undone.`)) return;
  try {
    await api(`api/orders/${id}/status`, jsonRequest('PUT', { status: 'CANCELLED' }));
    bootstrap.Modal.getOrCreateInstance($('orderModal')).hide();
    toast(`Order #${id} cancelled`);
    loadOrders();
  } catch (error) { toast(error.message, true); }
}

/* ---------- events ---------- */
document.addEventListener('click', (event) => {
  const button = event.target.closest('button'); if (!button) return;
  if (button.id === 'retryMenu') loadFoods();
  else if (button.dataset.add) addToCart(Number(button.dataset.add));
  else if (button.dataset.inc) changeQty(Number(button.dataset.inc), 1);
  else if (button.dataset.dec) changeQty(Number(button.dataset.dec), -1);
  else if (button.dataset.del) changeQty(Number(button.dataset.del), -999);
  else if (button.dataset.cat) { activeCategory = button.dataset.cat; renderFilters(); renderMenu(); }
  else if (button.dataset.order) showOrder(Number(button.dataset.order));
  else if (button.dataset.cancel) cancelOrder(Number(button.dataset.cancel));
});
$('cartBtn').addEventListener('click', showCart);
$('ordersBtn').addEventListener('click', loadOrders);
$('refreshOrders').addEventListener('click', loadOrders);
$('backToMenu').addEventListener('click', () => $('menuSection').scrollIntoView({ behavior: 'smooth' }));
$('checkout').addEventListener('submit', placeOrder);
$('menuSearch').addEventListener('input', (event) => { searchTerm = event.target.value; renderMenu(); });
$('customerName').addEventListener('input', () => $('customerName').classList.remove('is-invalid'));
$('customerPhone').addEventListener('input', () => $('customerPhone').classList.remove('is-invalid'));
loadFoods();
