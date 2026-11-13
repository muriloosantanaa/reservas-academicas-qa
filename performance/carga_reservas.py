#!/usr/bin/env python3
"""
Teste de carga e concorrência do ReservaHub (somente biblioteca padrão do Python 3).

Complementa o plano JMeter (performance/reservas-carga.jmx): serve para obter, sem
instalar nada, números reais de latência/vazão e a prova de que N requisições
simultâneas pelo mesmo recurso geram UMA reserva.

Uso:   python3 performance/carga_reservas.py [--base http://localhost:8080]
                                              [--leitores 20] [--leituras 25] [--simultaneas 50]
Saída: relatório em texto no terminal e em performance/resultados/ultimo-resultado.md
"""
import argparse, base64, datetime as dt, http.cookiejar, json, os, statistics, threading, time, urllib.error, urllib.request, uuid

USERS = {"admin": "Admin123!", "professor": "Prof123!"}


def novo_cliente(base):
    """Cada 'usuário virtual' guarda cookies (como um navegador ou o Cookie Manager do JMeter)."""
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))

    def chamar(metodo, caminho, usuario=None, corpo=None):
        cab = {"Content-Type": "application/json"}
        if usuario:
            cab["Authorization"] = "Basic " + base64.b64encode(f"{usuario}:{USERS[usuario]}".encode()).decode()
        dados = json.dumps(corpo).encode() if corpo is not None else None
        req = urllib.request.Request(base + caminho, data=dados, method=metodo, headers=cab)
        t0 = time.perf_counter()
        try:
            with opener.open(req, timeout=60) as r:
                texto = r.read().decode()
                status = r.status
        except urllib.error.HTTPError as e:
            texto, status = e.read().decode(), e.code
        ms = (time.perf_counter() - t0) * 1000
        try:
            return status, ms, json.loads(texto) if texto else None
        except ValueError:
            return status, ms, texto
    return chamar


def pct(valores, p):
    v = sorted(valores)
    return v[min(len(v) - 1, int(round(p / 100 * (len(v) - 1))))]


def resumo(nome, latencias, erros, duracao_s):
    n = len(latencias)
    return {
        "cenario": nome, "requisicoes": n, "erros_5xx_ou_falha": erros,
        "vazao_req_s": round(n / duracao_s, 1), "media_ms": round(statistics.mean(latencias), 1),
        "p50_ms": round(pct(latencias, 50), 1), "p95_ms": round(pct(latencias, 95), 1),
        "p99_ms": round(pct(latencias, 99), 1), "max_ms": round(max(latencias), 1),
    }


def rodar_paralelo(n, tarefa):
    barreira = threading.Barrier(n)
    saidas = [None] * n

    def alvo(i):
        barreira.wait()
        saidas[i] = tarefa(i)
    ts = [threading.Thread(target=alvo, args=(i,)) for i in range(n)]
    t0 = time.perf_counter()
    [t.start() for t in ts]
    [t.join() for t in ts]
    return saidas, time.perf_counter() - t0


def horario(dias, hora=8, dur=2):
    b = (dt.datetime.now() + dt.timedelta(days=dias)).replace(hour=hora, minute=0, second=0, microsecond=0)
    return b.isoformat(timespec="seconds"), (b + dt.timedelta(hours=dur)).isoformat(timespec="seconds")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--leitores", type=int, default=20)
    ap.add_argument("--leituras", type=int, default=25)
    ap.add_argument("--simultaneas", type=int, default=50)
    a = ap.parse_args()
    admin = novo_cliente(a.base)
    sufixo = uuid.uuid4().hex[:6]

    def criar(caminho, corpo):
        s, _, b = admin("POST", caminho, "admin", corpo)
        assert s == 200, (caminho, s, b)
        return b["id"]

    relatorio = []

    # ---- Cenário 1: leitura autenticada (GET /api/salas) -----------------------------------
    def leitor(_):
        c = novo_cliente(a.base)
        lat, err = [], 0
        for _ in range(a.leituras):
            s, ms, _ = c("GET", "/api/salas", "professor")
            lat.append(ms)
            err += 0 if s == 200 else 1
        return lat, err
    saidas, dur = rodar_paralelo(a.leitores, leitor)
    lat = [x for l, _ in saidas for x in l]
    relatorio.append(resumo(f"Leitura autenticada: {a.leitores} usuários x {a.leituras} GET /api/salas", lat, sum(e for _, e in saidas), dur))

    # ---- Cenário 2: N reservas simultâneas pelo MESMO recurso ------------------------------
    sala = criar("/api/admin/salas", {"nome": f"Carga-{sufixo}", "localizacao": "Bloco C", "capacidade": 40})
    profs = [criar("/api/admin/professores", {"nome": f"P{i}", "email": f"c{sufixo}{i}@x.edu.br", "competencia": "Java"}) for i in range(a.simultaneas)]
    ini, fim = horario(200)

    def disputa(i):
        c = novo_cliente(a.base)
        s, ms, _ = c("POST", "/api/reservas", "professor",
                     {"sala": {"id": sala}, "professor": {"id": profs[i]}, "inicio": ini, "fim": fim, "finalidade": "Carga"})
        return s, ms
    saidas, dur = rodar_paralelo(a.simultaneas, disputa)
    status = [s for s, _ in saidas]
    r2 = resumo(f"{a.simultaneas} reservas simultâneas, mesma sala e horário", [m for _, m in saidas], sum(1 for s in status if s >= 500), dur)
    r2["aceitas_200"] = status.count(200)
    r2["recusadas_409"] = status.count(409)
    r2["outros"] = sorted(set(s for s in status if s not in (200, 409)))
    r2["invariante_uma_aceita"] = status.count(200) == 1 and status.count(409) == a.simultaneas - 1
    relatorio.append(r2)

    # ---- Cenário 3: N reservas simultâneas de recursos DIFERENTES (não deve haver espera) ---
    salas3 = [criar("/api/admin/salas", {"nome": f"Par-{sufixo}-{i}", "localizacao": "Bloco D", "capacidade": 20}) for i in range(a.simultaneas)]
    profs3 = [criar("/api/admin/professores", {"nome": f"Q{i}", "email": f"d{sufixo}{i}@x.edu.br", "competencia": "SQL"}) for i in range(a.simultaneas)]
    ini3, fim3 = horario(201)

    def paralela(i):
        c = novo_cliente(a.base)
        s, ms, _ = c("POST", "/api/reservas", "professor",
                     {"sala": {"id": salas3[i]}, "professor": {"id": profs3[i]}, "inicio": ini3, "fim": fim3, "finalidade": "Carga"})
        return s, ms
    saidas, dur = rodar_paralelo(a.simultaneas, paralela)
    status3 = [s for s, _ in saidas]
    r3 = resumo(f"{a.simultaneas} reservas simultâneas de recursos diferentes", [m for _, m in saidas], sum(1 for s in status3 if s >= 500), dur)
    r3["aceitas_200"] = status3.count(200)
    r3["todas_aceitas"] = status3.count(200) == a.simultaneas
    relatorio.append(r3)

    # ---- Saída ------------------------------------------------------------------------------
    linhas = ["# Resultado do teste de carga (carga_reservas.py)", "",
              f"Data: {dt.datetime.now():%Y-%m-%d %H:%M}  |  Alvo: {a.base}", "",
              "| Cenário | Req. | Erros 5xx | Vazão (req/s) | Média (ms) | p50 | p95 | p99 | Máx |",
              "|---|---|---|---|---|---|---|---|---|"]
    for r in relatorio:
        linhas.append(f"| {r['cenario']} | {r['requisicoes']} | {r['erros_5xx_ou_falha']} | {r['vazao_req_s']} | {r['media_ms']} | {r['p50_ms']} | {r['p95_ms']} | {r['p99_ms']} | {r['max_ms']} |")
    linhas += ["", "Invariantes de concorrência:",
               f"- Mesma sala/horário: {r2['aceitas_200']} aceita(s) e {r2['recusadas_409']} recusada(s) com 409; outros códigos: {r2['outros'] or 'nenhum'}. Invariante 'exatamente uma aceita': **{'OK' if r2['invariante_uma_aceita'] else 'FALHOU'}**.",
               f"- Recursos diferentes: {r3['aceitas_200']}/{a.simultaneas} aceitas. Invariante 'todas aceitas': **{'OK' if r3['todas_aceitas'] else 'FALHOU'}**."]
    texto = "\n".join(linhas)
    print(texto)
    os.makedirs(os.path.join(os.path.dirname(__file__), "resultados"), exist_ok=True)
    with open(os.path.join(os.path.dirname(__file__), "resultados", "ultimo-resultado.md"), "w", encoding="utf-8") as f:
        f.write(texto + "\n")
    ok = r2["invariante_uma_aceita"] and r3["todas_aceitas"] and all(r["erros_5xx_ou_falha"] == 0 for r in relatorio)
    raise SystemExit(0 if ok else 1)


if __name__ == "__main__":
    main()
