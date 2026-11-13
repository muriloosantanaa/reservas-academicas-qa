const $ = (selector) => document.querySelector(selector);

let auth = null;
let username = "";
let isManager = false;
let isAdmin = false;
let rooms = [];
let professors = [];
let materials = [];

async function api(path, options = {}) {
    const headers = {
        "Content-Type": "application/json",
        ...(auth ? { Authorization: `Basic ${auth}` } : {}),
        ...(options.headers || {})
    };

    const response = await fetch(path, {
        ...options,
        headers
    });

    if (response.status === 204) {
        return null;
    }

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
        throw new Error(data.message || `Erro ${response.status}`);
    }

    return data;
}

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (character) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[character]);
}

function formatDateTime(value) {
    return new Date(value).toLocaleString("pt-BR");
}

function formatTime(value) {
    return new Date(value).toLocaleTimeString("pt-BR", {
        hour: "2-digit",
        minute: "2-digit"
    });
}

async function login(event) {
    event.preventDefault();

    username = $("#username").value.trim();
    auth = btoa(`${username}:${$("#password").value}`);
    isManager = ["admin", "responsavel"].includes(username.toLowerCase());
    isAdmin = username.toLowerCase() === "admin";

    try {
        await load();

        $("#login").classList.add("hidden");
        $("#app").classList.remove("hidden");
        $("#userLabel").textContent = username;
        $("#logout").classList.remove("hidden");

        ["#addRoom", "#addProfessor", "#addMaterial"].forEach((selector) => {
            $(selector).classList.toggle("hidden", !isAdmin);
        });
    } catch (error) {
        auth = null;
        username = "";
        isManager = false;
        isAdmin = false;
        $("#loginError").textContent =
            "Não foi possível autenticar. Confira usuário e senha.";
    }
}

function logout() {
    auth = null;
    username = "";
    isManager = false;
    isAdmin = false;

    $("#app").classList.add("hidden");
    $("#login").classList.remove("hidden");
    $("#logout").classList.add("hidden");
    $("#userLabel").textContent = "Não autenticado";
    $("#loginError").textContent = "";
}

function renderBookingActions(reservation) {
    const actions = [];
    const isOwner = reservation.solicitanteUsuario === username;

    if (isManager && reservation.status === "SOLICITADA") {
        actions.push(
            `<button class="action" onclick="changeStatus(${reservation.id}, 'aprovar')">Aprovar</button>`,
            `<button class="action" onclick="changeStatus(${reservation.id}, 'rejeitar')">Rejeitar</button>`
        );
    }

    if (isManager && reservation.status === "APROVADA") {
        actions.push(
            `<button class="action" onclick="changeStatus(${reservation.id}, 'iniciar')">Iniciar</button>`,
            `<button class="action" onclick="changeStatus(${reservation.id}, 'nao-compareceu')">Não compareceu</button>`
        );
    }

    if (isManager && reservation.status === "EM_USO") {
        actions.push(
            `<button class="action" onclick="changeStatus(${reservation.id}, 'concluir')">Concluir</button>`
        );
    }

    if (
        (isOwner || isManager) &&
        ["SOLICITADA", "APROVADA"].includes(reservation.status)
    ) {
        actions.push(
            `<button class="action" onclick="changeStatus(${reservation.id}, 'cancelar')">Cancelar</button>`
        );
    }

    return actions.join("");
}

function renderBookings(bookings) {
    $("#bookings").innerHTML = bookings.map((reservation) => `
        <tr>
            <td>
                #${reservation.id}
                <br>
                <small>${escapeHtml(reservation.finalidade)}</small>
            </td>
            <td>${escapeHtml(reservation.sala?.nome)}</td>
            <td>${escapeHtml(reservation.professor?.nome)}</td>
            <td>
                ${formatDateTime(reservation.inicio)}
                <br>
                ${formatTime(reservation.fim)}
            </td>
            <td>
                <span class="status ${reservation.status}">
                    ${reservation.status.replaceAll("_", " ")}
                </span>
            </td>
            <td>${renderBookingActions(reservation)}</td>
        </tr>
    `).join("") || `
        <tr>
            <td colspan="6">Nenhuma reserva cadastrada.</td>
        </tr>
    `;
}

function renderCatalogs() {
    $("#roomList").innerHTML = rooms.map((room) => `
        <div class="mini-item">
            <span>
                ${escapeHtml(room.nome)}
                <small>· ${room.capacidade} lugares</small>
            </span>
            <small>${room.ativa ? "Ativa" : "Inativa"}</small>
        </div>
    `).join("") || "<small>Nenhuma sala.</small>";

    $("#profList").innerHTML = professors.map((professor) => `
        <div class="mini-item">
            <span>${escapeHtml(professor.nome)}</span>
            <small>${professor.ativo ? "Ativo" : "Inativo"}</small>
        </div>
    `).join("") || "<small>Nenhum professor.</small>";

    $("#materialList").innerHTML = materials.map((material) => `
        <div class="mini-item">
            <span>${escapeHtml(material.nome)}</span>
            <small>${material.quantidade} un.</small>
        </div>
    `).join("") || "<small>Nenhum material.</small>";
}

async function load() {
    [rooms, professors, materials] = await Promise.all([
        api("/api/salas"),
        api("/api/professores"),
        api("/api/materiais")
    ]);

    const bookings = await api("/api/reservas");

    $("#total").textContent = bookings.length;
    $("#pending").textContent = bookings.filter(
        (reservation) => reservation.status === "SOLICITADA"
    ).length;
    $("#rooms").textContent = rooms.filter((room) => room.ativa).length;
    $("#materials").textContent = materials.length;

    renderBookings(bookings);
    renderCatalogs();
}

async function changeStatus(id, action) {
    try {
        await api(`/api/reservas/${id}/${action}`, {
            method: "POST"
        });
        await load();
    } catch (error) {
        alert(error.message);
    }
}

function openBookingDialog() {
    const activeRooms = rooms.filter((room) => room.ativa && !room.emManutencao);
    const activeProfessors = professors.filter((professor) => professor.ativo);
    const availableMaterials = materials.filter(
        (material) => material.disponivel && !material.emManutencao
    );

    if (!activeRooms.length || !activeProfessors.length) {
        alert("Cadastre ao menos uma sala ativa e um professor ativo.");
        return;
    }

    $("#roomSelect").innerHTML = activeRooms.map((room) => `
        <option value="${room.id}">${escapeHtml(room.nome)}</option>
    `).join("");

    $("#profSelect").innerHTML = activeProfessors.map((professor) => `
        <option value="${professor.id}">${escapeHtml(professor.nome)}</option>
    `).join("");

    $("#materialSelect").innerHTML = `
        <option value="">Sem material</option>
        ${availableMaterials.map((material) => `
            <option value="${material.id}">${escapeHtml(material.nome)}</option>
        `).join("")}
    `;

    $("#bookingError").textContent = "";
    $("#bookingDialog").showModal();
}

async function createBooking(event) {
    event.preventDefault();

    const materialId = $("#materialSelect").value;
    const payload = {
        sala: { id: Number($("#roomSelect").value) },
        professor: { id: Number($("#profSelect").value) },
        material: materialId ? { id: Number(materialId) } : null,
        inicio: $("#start").value,
        fim: $("#end").value,
        finalidade: $("#purpose").value.trim()
    };

    try {
        await api("/api/reservas", {
            method: "POST",
            body: JSON.stringify(payload)
        });

        $("#bookingDialog").close();
        event.target.reset();
        await load();
    } catch (error) {
        $("#bookingError").textContent = error.message;
    }
}

const catalogForms = {
    room: {
        title: "Nova sala",
        endpoint: "salas",
        fields: [
            ["nome", "Nome", "text"],
            ["localizacao", "Localização", "text"],
            ["capacidade", "Capacidade", "number"]
        ]
    },
    professor: {
        title: "Novo professor",
        endpoint: "professores",
        fields: [
            ["nome", "Nome", "text"],
            ["email", "E-mail", "email"]
        ]
    },
    material: {
        title: "Novo material",
        endpoint: "materiais",
        fields: [
            ["nome", "Nome", "text"],
            [
                "tipo",
                "Tipo (PROJETOR, NOTEBOOK, MICROFONE, CAIXA_SOM, OUTRO)",
                "text"
            ],
            ["quantidade", "Quantidade", "number"]
        ]
    }
};

function openCatalogDialog(type) {
    const config = catalogForms[type];

    $("#simpleTitle").textContent = config.title;
    $("#simpleError").textContent = "";
    $("#simpleFields").innerHTML = config.fields.map(
        ([id, label, inputType]) => `
            <label>
                ${label}
                <input id="field_${id}" type="${inputType}" required>
            </label>
        `
    ).join("");

    $("#simpleForm").dataset.type = type;
    $("#simpleDialog").showModal();
}

async function saveCatalogItem(event) {
    event.preventDefault();

    const type = event.target.dataset.type;
    const config = catalogForms[type];
    const payload = {};

    $("#simpleFields").querySelectorAll("input").forEach((input) => {
        const key = input.id.replace("field_", "");
        payload[key] = input.type === "number"
            ? Number(input.value)
            : input.value.trim();
    });

    if (type === "room") {
        payload.ativa = true;
        payload.emManutencao = false;
    }

    if (type === "professor") {
        payload.ativo = true;
    }

    if (type === "material") {
        payload.tipo = payload.tipo.toUpperCase();
        payload.disponivel = true;
        payload.restrito = false;
        payload.emManutencao = false;
    }

    try {
        await api(`/api/admin/${config.endpoint}`, {
            method: "POST",
            body: JSON.stringify(payload)
        });

        $("#simpleDialog").close();
        event.target.reset();
        await load();
    } catch (error) {
        $("#simpleError").textContent = error.message;
    }
}

$("#loginForm").addEventListener("submit", login);
$("#logout").addEventListener("click", logout);
$("#refresh").addEventListener("click", load);
$("#newBooking").addEventListener("click", openBookingDialog);
$("#bookingForm").addEventListener("submit", createBooking);
$("#addRoom").addEventListener("click", () => openCatalogDialog("room"));
$("#addProfessor").addEventListener(
    "click",
    () => openCatalogDialog("professor")
);
$("#addMaterial").addEventListener(
    "click",
    () => openCatalogDialog("material")
);
$("#simpleForm").addEventListener("submit", saveCatalogItem);

document.querySelectorAll("[data-close]").forEach((button) => {
    button.addEventListener("click", () => {
        button.closest("dialog").close();
    });
});

window.changeStatus = changeStatus;
