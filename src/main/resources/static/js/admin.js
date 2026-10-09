'use strict';
/* Admin page: menu CRUD (POST/PUT/DELETE /api/foods) and order status changes (PUT /api/orders/{id}/status).
   Shared helpers ($, money, esc, api, toast ...) come from common.js. */

let foods = [];
let orders = [];
let deleteTarget = null;

const foodModal = () => bootstrap.Modal.getOrCreateInstance($('foodModal'));
const deleteModal = () => bootstrap.Modal.getOrCreateInstance($('deleteModal'));

/* ---------- menu ---------- */
async function loadFoods() {
  try {
    foods = await api('api/foods');
    renderFoods();
    $('categoryList').innerHTML = [...new Set(foods.map((f) => f.category).filter(Boolean))].map((c) => `<option value="${esc(c)}">`).join('');
  } catch (error) {
    $('foodTableBox').innerHTML = `<div class="alert alert-warning"><i class="bi bi-exclamation-triangle me-2"></i>${esc(error.message)}</div>`;
  }
  renderStats();
}

function renderFoods() {
  if (!foods.length) {
    $('foodTableBox').innerHTML = '<div class="empty-state"><i class="bi bi-basket d-block mb-2"></i><strong>The menu is empty</strong><p class="mb-0">Use “Add dish” to create the first item.</p></div>';
    return;
  }
  $('foodTableBox').innerHTML = `<div class="table-responsive border rounded-4"><table class="table admin-table table-hover mb-0"><thead><tr><th>Dish</th><th>Category</th><th class="text-end">Price</th><th class="text-center">Available</th><th class="text-end">Actions</th></tr></thead><tbody>${foods.map((food) => `
    <tr><td><strong>${esc(food.name)}</strong><div class="small text-secondary">${esc(food.description || '')}</div></td>
    <td>${esc(food.category || '—')}</td><td class="text-end text-nowrap">${money(food.price)}</td>
    <td class="text-center"><div class="form-check form-switch d-inline-block"><input class="form-check-input" type="checkbox" role="switch" data-toggle="${Number(food.id)}" ${food.available ? 'checked' : ''} aria-label="Available: ${esc(food.name)}"></div></td>
    <td class="text-end text-nowrap"><button class="btn btn-sm btn-outline-secondary rounded-pill me-1" type="button" data-edit="${Number(food.id)}"><i class="bi bi-pencil"></i><span class="visually-hidden">Edit ${esc(food.name)}</span></button><button class="btn btn-sm btn-outline-danger rounded-pill" type="button" data-delete="${Number(food.id)}"><i class="bi bi-trash3"></i><span class="visually-hidden">Delete ${esc(food.name)}</span></button></td></tr>`).join('')}</tbody></table></div>`;
}

function openFoodModal(id) {
  const food = foods.find((item) => item.id === id);
  $('foodForm').classList.remove('was-validated');
  $('foodFormError').classList.add('d-none');
  $('foodModalTitle').textContent = food ? 'Edit dish' : 'Add dish';
  $('foodId').value = food ? food.id : '';
  $('foodName').value = food ? food.name : '';
  $('foodCategory').value = food ? (food.category || '') : '';
  $('foodPrice').value = food ? Number(food.price).toFixed(2) : '';
  $('foodDescription').value = food ? (food.description || '') : '';
  $('foodAvailable').checked = food ? food.available : true;
  foodModal().show();
}

function foodPayload(food, overrides = {}) {
  return { name: food.name, category: food.category, description: food.description, price: food.price, available: food.available, ...overrides };
}

async function saveFood(event) {
  event.preventDefault();
  const form = $('foodForm');
  form.classList.add('was-validated');
  if (!form.checkValidity()) return;
  const id = $('foodId').value;
  const payload = {
    name: $('foodName').value.trim(), category: $('foodCategory').value.trim(),
    description: $('foodDescription').value.trim(), price: Number($('foodPrice').value), available: $('foodAvailable').checked,
  };
  const button = $('saveFoodBtn'); button.disabled = true;
  try {
    await api(id ? `api/foods/${id}` : 'api/foods', jsonRequest(id ? 'PUT' : 'POST', payload));
    foodModal().hide();
    toast(id ? 'Dish updated' : 'Dish added');
    await loadFoods();
  } catch (error) {
    $('foodFormError').textContent = error.message;
    $('foodFormError').classList.remove('d-none');
  } finally { button.disabled = false; }
}

async function toggleAvailable(id, available) {
  const food = foods.find((item) => item.id === id);
  if (!food) return;
  try {
    await api(`api/foods/${id}`, jsonRequest('PUT', foodPayload(food, { available })));
    toast(`${food.name} is now ${available ? 'available' : 'unavailable'}`);
  } catch (error) { toast(error.message, true); }
  await loadFoods();
}

function askDelete(id) {
  deleteTarget = foods.find((item) => item.id === id) || null;
  if (!deleteTarget) return;
  $('deleteName').textContent = deleteTarget.name;
  deleteModal().show();
}

async function confirmDelete() {
  if (!deleteTarget) return;
  try {
    await api(`api/foods/${deleteTarget.id}`, { method: 'DELETE' });
    toast(`${deleteTarget.name} deleted`);
  } catch (error) { toast(error.message, true); }
  deleteTarget = null;
  deleteModal().hide();
  await loadFoods();
}

/* ---------- orders ---------- */
async function loadOrders() {
  const status = $('statusFilter').value;
  $('orderTableBox').innerHTML = '<div class="text-center py-4"><span class="spinner-border text-secondary" role="status"><span class="visually-hidden">Loading orders...</span></span></div>';
  try {
    orders = await api(status ? `api/orders?status=${encodeURIComponent(status)}` : 'api/orders');
    renderOrders();
  } catch (error) {
    $('orderTableBox').innerHTML = `<div class="alert alert-warning"><i class="bi bi-exclamation-triangle me-2"></i>${esc(error.message)}</div>`;
  }
  loadStatsOrders();
}

function statusButton(order, next) {
  const danger = next === 'CANCELLED';
  return `<button type="button" class="btn btn-sm ${danger ? 'btn-outline-danger' : 'btn-brand'} rounded-pill" data-order="${Number(order.id)}" data-next="${esc(next)}">${danger ? 'Cancel' : `Mark ${esc(next.toLowerCase())}`}</button>`;
}

function renderOrders() {
  if (!orders.length) {
    $('orderTableBox').innerHTML = '<div class="empty-state"><i class="bi bi-receipt d-block mb-2"></i><strong>No orders found</strong><p class="mb-0">Orders appear here as customers place them.</p></div>';
    return;
  }
  $('orderTableBox').innerHTML = `<div class="table-responsive border rounded-4"><table class="table admin-table table-hover mb-0"><thead><tr><th>Order</th><th>Customer</th><th>Placed</th><th>Items</th><th class="text-end">Total</th><th>Status</th><th class="text-end">Next step</th></tr></thead><tbody>${orders.map((order) => `
    <tr><td class="fw-bold">#${Number(order.id)}</td><td>${esc(order.customerName)}<div class="small text-secondary">${esc(order.customerPhone || '')}</div></td>
    <td class="text-nowrap">${esc(formatDate(order.orderTime))}</td>
    <td class="small">${(order.items || []).map((i) => `${esc(i.foodName)} × ${Number(i.quantity)}`).join('<br>')}</td>
    <td class="text-end fw-bold text-nowrap">${money(order.total)}</td><td>${statusBadge(order.status)}</td>
    <td class="text-end"><div class="d-flex gap-1 justify-content-end flex-wrap">${(order.nextStatuses || []).length ? order.nextStatuses.map((next) => statusButton(order, next)).join('') : '<span class="text-secondary small">Finished</span>'}</div></td></tr>`).join('')}</tbody></table></div>`;
}

async function changeStatus(id, next) {
  if (next === 'CANCELLED' && !window.confirm(`Cancel order #${id}?`)) return;
  try {
    await api(`api/orders/${id}/status`, jsonRequest('PUT', { status: next }));
    toast(`Order #${id} is now ${next}`);
  } catch (error) { toast(error.message, true); }
  await loadOrders();
}

/* ---------- summary cards ---------- */
let allOrders = [];
async function loadStatsOrders() {
  try { allOrders = await api('api/orders'); } catch (_) { allOrders = []; }
  renderStats();
}
function renderStats() {
  $('statDishes').textContent = foods.length;
  $('statOrders').textContent = allOrders.length;
  const revenue = allOrders.filter((o) => o.status !== 'CANCELLED').reduce((sum, o) => sum + Number(o.total), 0);
  $('statRevenue').textContent = money(revenue);
  $('statOpen').textContent = allOrders.filter((o) => (o.nextStatuses || []).length > 0).length;
}

/* ---------- events ---------- */
document.addEventListener('click', (event) => {
  const button = event.target.closest('button'); if (!button) return;
  if (button.id === 'addFoodBtn') openFoodModal(null);
  else if (button.dataset.edit) openFoodModal(Number(button.dataset.edit));
  else if (button.dataset.delete) askDelete(Number(button.dataset.delete));
  else if (button.dataset.next) changeStatus(Number(button.dataset.order), button.dataset.next);
});
document.addEventListener('change', (event) => {
  const input = event.target;
  if (input.dataset && input.dataset.toggle) toggleAvailable(Number(input.dataset.toggle), input.checked);
});
$('foodForm').addEventListener('submit', saveFood);
$('confirmDeleteBtn').addEventListener('click', confirmDelete);
$('statusFilter').addEventListener('change', loadOrders);
$('refreshAdminOrders').addEventListener('click', loadOrders);
$('ordersTab').addEventListener('shown.bs.tab', loadOrders);

loadFoods();
loadStatsOrders();
