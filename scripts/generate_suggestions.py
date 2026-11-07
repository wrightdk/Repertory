#!/usr/bin/env python3
"""Generate app/src/main/assets/suggestions.json, the starter suggestions on Home.

Looks up a hand-picked list of well-known directors, actors, cinematographers,
writers and composers on TMDB (/search/person) and writes their TMDB id, name
and department. Run it by hand when the list changes and commit the output;
the app build never runs it and never needs network access.

    python3 scripts/generate_suggestions.py

The TMDB key is read the same way the app build reads it: local.properties,
then the TMDB_API_KEY environment variable, then gradle.properties.
"""

import json
import os
import sys
import time
import unicodedata
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUTPUT = os.path.join(ROOT, "app", "src", "main", "assets", "suggestions.json")

# Department names are TMDB's, as stored in TrackedPerson.department.
PEOPLE = {
    "Directing": [
        "Chantal Akerman", "Pedro Almodóvar", "Paul Thomas Anderson", "Wes Anderson",
        "Ingmar Bergman", "Kathryn Bigelow", "Bong Joon Ho", "Luis Buñuel",
        "Jane Campion", "Joel Coen", "Francis Ford Coppola", "Sofia Coppola",
        "Claire Denis", "Ava DuVernay", "Rainer Werner Fassbinder", "Federico Fellini",
        "David Fincher", "John Ford", "Greta Gerwig", "Jean-Luc Godard",
        "Michael Haneke", "Werner Herzog", "Alfred Hitchcock", "Barry Jenkins",
        "Abbas Kiarostami", "Krzysztof Kieślowski", "Hirokazu Kore-eda", "Stanley Kubrick",
        "Akira Kurosawa", "Spike Lee", "Sergio Leone", "David Lynch",
        "Hayao Miyazaki", "Christopher Nolan", "Yasujirō Ozu", "Park Chan-wook",
        "Jordan Peele", "Lynne Ramsay", "Satyajit Ray", "Kelly Reichardt",
        "Céline Sciamma", "Martin Scorsese", "Steven Spielberg", "Quentin Tarantino",
        "Andrei Tarkovsky", "François Truffaut", "Agnès Varda", "Denis Villeneuve",
        "Orson Welles", "Billy Wilder", "Wong Kar-wai", "Chloé Zhao",
    ],
    "Acting": [
        "Juliette Binoche", "Cate Blanchett", "Humphrey Bogart", "Marlon Brando",
        "Michael Caine", "Maggie Cheung", "Matt Damon", "Bette Davis",
        "Viola Davis", "Daniel Day-Lewis", "Robert De Niro", "Cary Grant",
        "Tom Hanks", "Audrey Hepburn", "Katharine Hepburn", "Isabelle Huppert",
        "Tony Leung Chiu-wai", "Sophia Loren", "Marcello Mastroianni", "Frances McDormand",
        "Toshirō Mifune", "Julianne Moore", "Jack Nicholson", "Gary Oldman",
        "Al Pacino", "Joaquin Phoenix", "Gena Rowlands", "Song Kang-ho",
        "James Stewart", "Meryl Streep", "Tilda Swinton", "Denzel Washington",
    ],
    "Camera": [
        "Néstor Almendros", "Roger Deakins", "Christopher Doyle", "Agnès Godard",
        "Janusz Kamiński", "Emmanuel Lubezki", "Robby Müller", "Sven Nykvist",
        "Vittorio Storaro", "Hoyte van Hoytema", "Gordon Willis",
    ],
    "Writing": [
        "Jean-Claude Carrière", "Paddy Chayefsky", "Nora Ephron", "William Goldman",
        "Ben Hecht", "Charlie Kaufman", "Aaron Sorkin", "Robert Towne",
    ],
    "Sound": [
        "Philip Glass", "Jerry Goldsmith", "Jonny Greenwood", "Hildur Guðnadóttir",
        "Bernard Herrmann", "Joe Hisaishi", "Michel Legrand", "Mica Levi",
        "Ennio Morricone", "Nino Rota", "Ryuichi Sakamoto", "John Williams",
        "Hans Zimmer",
    ],
}


def read_properties(path):
    props = {}
    try:
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    key, value = line.split("=", 1)
                    props[key.strip()] = value.strip()
    except FileNotFoundError:
        pass
    return props


def api_key():
    for value in (
        read_properties(os.path.join(ROOT, "local.properties")).get("TMDB_API_KEY"),
        os.environ.get("TMDB_API_KEY"),
        read_properties(os.path.join(ROOT, "gradle.properties")).get("TMDB_API_KEY"),
    ):
        if value:
            return value
    sys.exit("No TMDB_API_KEY in local.properties, the environment or gradle.properties.")


def fold(text):
    """Lower case without accents, so "Toshirō" matches "Toshiro"."""
    decomposed = unicodedata.normalize("NFKD", text)
    return "".join(c for c in decomposed if not unicodedata.combining(c)).casefold()


def search(key, name):
    query = urllib.parse.urlencode({"api_key": key, "query": name, "include_adult": "false"})
    request = urllib.request.Request(
        f"https://api.themoviedb.org/3/search/person?{query}",
        headers={"Accept": "application/json", "User-Agent": "repertory-suggestions-script"},
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)["results"]


def pick(results, name, department):
    """The most popular result with this name, preferring one known for [department]."""
    same_name = [r for r in results if fold(r["name"]) == fold(name)] or results[:1]
    if not same_name:
        return None
    in_department = [r for r in same_name if r.get("known_for_department") == department]
    return max(in_department or same_name, key=lambda r: r.get("popularity", 0))


def main():
    key = api_key()
    suggestions, problems = [], []
    for department, names in PEOPLE.items():
        for name in names:
            result = pick(search(key, name), name, department)
            time.sleep(0.05)  # well under TMDB's rate limit
            if result is None:
                problems.append(f"not found: {name}")
                continue
            if fold(result["name"]) != fold(name):
                problems.append(f"name differs: {name} -> {result['name']} ({result['id']})")
            if result.get("known_for_department") != department:
                problems.append(
                    f"known for {result.get('known_for_department')}, listed as {department}: {name} ({result['id']})"
                )
            # Keep our spelling: TMDB sometimes drops diacritics.
            suggestions.append({"id": result["id"], "name": name, "department": department})

    ids = [s["id"] for s in suggestions]
    duplicates = {i for i in ids if ids.count(i) > 1}
    if duplicates:
        sys.exit(f"Duplicate TMDB ids: {sorted(duplicates)}")

    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", encoding="utf-8") as f:
        json.dump(suggestions, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print(f"Wrote {len(suggestions)} people to {os.path.relpath(OUTPUT, ROOT)}")
    for problem in problems:
        print("  check:", problem)


if __name__ == "__main__":
    main()
