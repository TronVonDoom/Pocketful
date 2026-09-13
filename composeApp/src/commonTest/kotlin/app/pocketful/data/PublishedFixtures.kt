package app.pocketful.data

/**
 * A catalog exactly as the Pocketful Editor publishes it.
 *
 * Not written by hand. Pocketful-Catalog's editor was run against a throwaway database, a small Base
 * Set was imported, reviewed, given pictures and published, and these are the index and set file
 * it wrote -- so these tests fail the day the app stops reading what the editor actually writes.
 * Four cards: Alakazam and Blastoise with pictures, Pikachu with a printing that has a picture of
 * its own, and Double Colorless Energy in a section, the last two published without a picture.
 */
object PublishedFixtures {
    /** catalog/index.json */
    val INDEX: String =
        """{"schema":2,"generatedAt":"2026-09-13T23:48:45+00:00","publicUrl":"http://127.0.0.1:55441/_store","catalogs":[""" +
        """{"id":"ptcg-en","game":"ptcg","language":"en","name":"English","nativeName":"English","back":"images/backs/ptc""" +
        """g-en.3a1ec6.webp","series":[{"id":"ptcg-en-base","code":"base","name":"Base","sets":[{"id":"ptcg-en-base01","c""" +
        """ode":"base01","file":"catalog/sets/ptcg-en-base01.v1.json.gz","kind":"expansion","logo":"images/logos/ptcg-en-""" +
        """base01.a0e206.webp","name":"Base Set","cards":4,"sha256":"a6e61faf720d2cd740c9d9cec8260373ea333d4fc66965b3ceb6""" +
        """77a9222285b9","version":1,"printings":19,"publishedAt":"2026-09-13T23:48:45+00:00","releaseDate":"1999-01-09",""" +
        """"abbreviation":"BS","printedTotal":102}]}]}],"words":{"normal":{"kind":"finish","label":"Normal","sort":1},"ho""" +
        """lo":{"kind":"finish","label":"Holo","sort":2},"reverse":{"kind":"finish","label":"Reverse Holo","sort":3},"len""" +
        """ticular":{"kind":"finish","label":"Lenticular","sort":4},"metal":{"kind":"finish","label":"Metal","sort":5},"1""" +
        """st-edition":{"kind":"edition","label":"1st Edition","sort":1},"shadowless":{"kind":"edition","label":"Shadowle""" +
        """ss","sort":2},"1999-2000-copyright":{"kind":"edition","label":"1999-2000 Copyright","sort":3},"1999-copyright"""" +
        """:{"kind":"edition","label":"1999 Copyright","sort":4},"no-e-reader":{"kind":"edition","label":"No e-Reader","s""" +
        """ort":5},"blue-border":{"kind":"edition","label":"Blue Border","sort":6},"gold-border":{"kind":"edition","label""" +
        """":"Gold Border","sort":7},"glossy":{"kind":"edition","label":"Glossy","sort":8},"peelable-ditto":{"kind":"edit""" +
        """ion","label":"Peelable Ditto","sort":9},"cosmos":{"kind":"pattern","label":"Cosmos Holo","sort":0},"cracked-ic""" +
        """e":{"kind":"pattern","label":"Cracked Ice Holo","sort":0},"duskball":{"kind":"pattern","label":"Dusk Ball Patt""" +
        """ern","sort":0},"energy":{"kind":"pattern","label":"Energy Pattern","sort":0},"friendball":{"kind":"pattern","l""" +
        """abel":"Friend Ball Pattern","sort":0},"galaxy":{"kind":"pattern","label":"Galaxy Holo","sort":0},"gold":{"kind""" +
        """":"pattern","label":"Gold","sort":0},"league":{"kind":"pattern","label":"League","sort":0},"loveball":{"kind":""" +
        """"pattern","label":"Love Ball Pattern","sort":0},"masterball":{"kind":"pattern","label":"Master Ball Pattern","""" +
        """sort":0},"mirror":{"kind":"pattern","label":"Mirror Holo","sort":0},"player-reward":{"kind":"pattern","label":""" +
        """"Player Rewards","sort":0},"pokeball":{"kind":"pattern","label":"Poké Ball Pattern","sort":0},"professor-progr""" +
        """am-foil":{"kind":"pattern","label":"Professor Program Foil","sort":0},"quickball":{"kind":"pattern","label":"Q""" +
        """uick Ball Pattern","sort":0},"rainbow":{"kind":"pattern","label":"Rainbow","sort":0},"starlight":{"kind":"patt""" +
        """ern","label":"Starlight Holo","sort":0},"team-rocket":{"kind":"pattern","label":"Team Rocket Pattern","sort":0""" +
        """},"tinsel":{"kind":"pattern","label":"Tinsel Holo","sort":0},"10th-anniversary":{"kind":"stamp","label":"10th """ +
        """Anniversary","sort":0},"1st-movie":{"kind":"stamp","label":"1st Movie","sort":0},"1st-movie-inverted":{"kind":""" +
        """"stamp","label":"1st Movie Inverted","sort":0},"25th-celebration":{"kind":"stamp","label":"25th Celebration","""" +
        """sort":0},"30th-pokeday":{"kind":"stamp","label":"30th Pokémon Day","sort":0},"ace-trainer":{"kind":"stamp","la""" +
        """bel":"Ace Trainer","sort":0},"asia-2023-24":{"kind":"stamp","label":"Asia 2023-24","sort":0},"asia-promo":{"ki""" +
        """nd":"stamp","label":"Asia Promo","sort":0},"bulbasaur":{"kind":"stamp","label":"Bulbasaur","sort":0},"champion""" +
        """":{"kind":"stamp","label":"Champion","sort":0},"charmander":{"kind":"stamp","label":"Charmander","sort":0},"ch""" +
        """icago-2009":{"kind":"stamp","label":"Chicago 2009","sort":0},"city-championships":{"kind":"stamp","label":"Cit""" +
        """y Championships","sort":0},"comic-con":{"kind":"stamp","label":"Comic-Con","sort":0},"countdown-calendar":{"ki""" +
        """nd":"stamp","label":"Countdown Calendar Stamp","sort":0},"destiny-deoxys":{"kind":"stamp","label":"Destiny Deo""" +
        """xys","sort":0},"distributor-meeting":{"kind":"stamp","label":"Distributor Meeting","sort":0},"eb-games":{"kind""" +
        """":"stamp","label":"EB Games Stamp","sort":0},"finalist":{"kind":"stamp","label":"Finalist","sort":0},"fossil-m""" +
        """useum":{"kind":"stamp","label":"Fossil Museum","sort":0},"games-expo":{"kind":"stamp","label":"Games Expo","so""" +
        """rt":0},"gamestop":{"kind":"stamp","label":"GameStop Stamp","sort":0},"gen-con":{"kind":"stamp","label":"Gen Co""" +
        """n","sort":0},"grey-star":{"kind":"stamp","label":"Grey Star","sort":0},"gym-challenge":{"kind":"stamp","label"""" +
        """:"Gym Challenge","sort":0},"horizons":{"kind":"stamp","label":"Horizons","sort":0},"illustration-contest-2022"""" +
        """:{"kind":"stamp","label":"Illustration Contest 2022","sort":0},"illustration-contest-2024":{"kind":"stamp","la""" +
        """bel":"Illustration Contest 2024","sort":0},"inquest-gamer":{"kind":"stamp","label":"InQuest Gamer","sort":0},"""" +
        """international-championship-europe":{"kind":"stamp","label":"Europe International Championships","sort":0},"int""" +
        """ernational-championship-latin-america":{"kind":"stamp","label":"Latin America International Championships","so""" +
        """rt":0},"international-championship-north-america":{"kind":"stamp","label":"North America International Champio""" +
        """nships","sort":0},"jr-stamp-rally":{"kind":"stamp","label":"Jr. Stamp Rally","sort":0},"judge":{"kind":"stamp"""" +
        ""","label":"Judge Stamp","sort":0},"kraze-club":{"kind":"stamp","label":"Kraze Club","sort":0},"master-ball-leag""" +
        """ue":{"kind":"stamp","label":"Master Ball League","sort":0},"mcdonalds":{"kind":"stamp","label":"McDonald's Sta""" +
        """mp","sort":0},"national-championships":{"kind":"stamp","label":"National Championships","sort":0},"nintendo-wo""" +
        """rld":{"kind":"stamp","label":"Nintendo World","sort":0},"origins":{"kind":"stamp","label":"Origins","sort":0},""" +
        """"origins-2008":{"kind":"stamp","label":"Origins 2008","sort":0},"pikachu":{"kind":"stamp","label":"Pikachu","s""" +
        """ort":0},"pikachu-tail":{"kind":"stamp","label":"Pikachu Tail","sort":0},"platinum":{"kind":"stamp","label":"Pl""" +
        """atinum","sort":0},"player-rewards-program":{"kind":"stamp","label":"Player Rewards","sort":0},"poke-ball-leagu""" +
        """e":{"kind":"stamp","label":"Poké Ball League","sort":0},"pokeball-stamp":{"kind":"stamp","label":"Poké Ball St""" +
        """amp","sort":0},"pokemon-4-ever":{"kind":"stamp","label":"Pokémon 4Ever","sort":0},"pokemon-center":{"kind":"st""" +
        """amp","label":"Pokémon Center Stamp","sort":0},"pokemon-center-ny":{"kind":"stamp","label":"Pokémon Center NY S""" +
        """tamp","sort":0},"pokemon-day":{"kind":"stamp","label":"Pokémon Day Stamp","sort":0},"pokemon-rocks-america":{"""" +
        """kind":"stamp","label":"Pokémon Rocks America","sort":0},"pokemon-together":{"kind":"stamp","label":"Pokémon To""" +
        """gether","sort":0},"poketour-99":{"kind":"stamp","label":"PokéTour '99","sort":0},"pop-tournament":{"kind":"sta""" +
        """mp","label":"POP Tournament","sort":0},"pre-release":{"kind":"stamp","label":"Prerelease Stamp","sort":0},"pro""" +
        """fessor-program":{"kind":"stamp","label":"Professor Program","sort":0},"quarter-finalist":{"kind":"stamp","labe""" +
        """l":"Quarter-Finalist","sort":0},"rain-city":{"kind":"stamp","label":"Rain City","sort":0},"regional-championsh""" +
        """ips":{"kind":"stamp","label":"Regional Championships","sort":0},"scrye":{"kind":"stamp","label":"Scrye","sort"""" +
        """:0},"semi-finalist":{"kind":"stamp","label":"Semi-Finalist","sort":0},"set-logo":{"kind":"stamp","label":"Set """ +
        """Logo Stamp","sort":0},"snowflake":{"kind":"stamp","label":"Snowflake Stamp","sort":0},"squirtle":{"kind":"stam""" +
        """p","label":"Squirtle","sort":0},"stadium-challenge":{"kind":"stamp","label":"Stadium Challenge","sort":0},"sta""" +
        """ff":{"kind":"stamp","label":"Staff Stamp","sort":0},"state-championships":{"kind":"stamp","label":"State Champ""" +
        """ionships","sort":0},"thank-you":{"kind":"stamp","label":"Thank You Stamp","sort":0},"top-eight":{"kind":"stamp""" +
        """","label":"Top 8","sort":0},"top-sixteen":{"kind":"stamp","label":"Top 16","sort":0},"top-thirty-two":{"kind":""" +
        """"stamp","label":"Top 32","sort":0},"trick-or-trade":{"kind":"stamp","label":"Trick or Trade Stamp","sort":0},"""" +
        """ultra-ball-league":{"kind":"stamp","label":"Ultra Ball League","sort":0},"w-promo":{"kind":"stamp","label":"W """ +
        """Promo Stamp","sort":0},"winner":{"kind":"stamp","label":"Winner Stamp","sort":0},"wizard-world-chicago":{"kind""" +
        """":"stamp","label":"Wizard World Chicago","sort":0},"wizard-world-philadelphia":{"kind":"stamp","label":"Wizard""" +
        """ World Philadelphia","sort":0},"worlds-2004":{"kind":"stamp","label":"Worlds 2004","sort":0},"worlds-2005":{"k""" +
        """ind":"stamp","label":"Worlds 2005","sort":0},"worlds-2007":{"kind":"stamp","label":"Worlds 2007","sort":0},"wo""" +
        """rlds-2008":{"kind":"stamp","label":"Worlds 2008","sort":0},"worlds-2009":{"kind":"stamp","label":"Worlds 2009"""" +
        ""","sort":0},"worlds-2010":{"kind":"stamp","label":"Worlds 2010","sort":0},"worlds-2022":{"kind":"stamp","label"""" +
        """:"Worlds 2022","sort":0},"worlds-2023":{"kind":"stamp","label":"Worlds 2023","sort":0},"worlds-2024":{"kind":"""" +
        """stamp","label":"Worlds 2024","sort":0},"worlds-2025":{"kind":"stamp","label":"Worlds 2025","sort":0},"wotc":{"""" +
        """kind":"stamp","label":"WotC","sort":0},"1st-edition-error":{"kind":"error","label":"1st Edition Error","sort":""" +
        """0},"1st-edition-scratch-error":{"kind":"error","label":"1st Edition Scratch Error","sort":0},"d-edition-error"""" +
        """:{"kind":"error","label":"D Edition Error","sort":0},"red-cheeks":{"kind":"error","label":"Red Cheeks","sort":""" +
        """0},"missing-expansion-symbol":{"kind":"error","label":"Missing Expansion Symbol","sort":0},"aoki-error":{"kind""" +
        """":"error","label":"Aoki Error","sort":0},"no-holo-error":{"kind":"error","label":"No Holo Error","sort":0},"ph""" +
        """anphy-error":{"kind":"error","label":"Phanphy Error","sort":0},"japanese-back":{"kind":"error","label":"Japane""" +
        """se Back","sort":0},"rarity-error":{"kind":"error","label":"Rarity Error","sort":0},"evolution-box-error":{"kin""" +
        """d":"error","label":"Evolution Box Error","sort":0},"d-ink-dot-error":{"kind":"error","label":"D Ink Dot Error"""" +
        ""","sort":0},"missing-hp":{"kind":"error","label":"Missing HP","sort":0},"missing-retreat-cost":{"kind":"error",""" +
        """"label":"Missing Retreat Cost","sort":0},"energy-symbol-error":{"kind":"error","label":"Energy Symbol Error","""" +
        """sort":0},"text-error":{"kind":"error","label":"Text Error","sort":0},"shifted-energy-cost":{"kind":"error","la""" +
        """bel":"Shifted Energy Cost","sort":0}},"terms":{"type":{"grass":{"en":"Grass"},"fire":{"en":"Fire"},"water":{"e""" +
        """n":"Water"},"lightning":{"en":"Lightning"},"psychic":{"en":"Psychic"},"fighting":{"en":"Fighting"},"darkness":""" +
        """{"en":"Darkness"},"metal":{"en":"Metal"},"fairy":{"en":"Fairy"},"dragon":{"en":"Dragon"},"colorless":{"en":"Co""" +
        """lorless"}},"rarity":{"common":{"en":"Common"},"uncommon":{"en":"Uncommon"},"rare":{"en":"Rare"},"holo-rare":{"""" +
        """en":"Holo Rare"},"promo":{"en":"Promo"},"holo-rare-lv-x":{"en":"Holo Rare LV.X"},"rare-prime":{"en":"Rare Prim""" +
        """e"},"legend":{"en":"LEGEND"},"holo-rare-v":{"en":"Holo Rare V"},"holo-rare-vmax":{"en":"Holo Rare VMAX"},"holo""" +
        """-rare-vstar":{"en":"Holo Rare VSTAR"},"amazing-rare":{"en":"Amazing Rare"},"radiant-rare":{"en":"Radiant Rare"""" +
        """},"shiny-rare":{"en":"Shiny Rare"},"shiny-rare-v":{"en":"Shiny Rare V"},"shiny-rare-vmax":{"en":"Shiny Rare VM""" +
        """AX"},"shiny-ultra-rare":{"en":"Shiny Ultra Rare"},"double-rare":{"en":"Double Rare"},"ultra-rare":{"en":"Ultra""" +
        """ Rare"},"illustration-rare":{"en":"Illustration Rare"},"special-illustration-rare":{"en":"Special Illustration""" +
        """ Rare"},"hyper-rare":{"en":"Hyper Rare"},"mega-hyper-rare":{"en":"Mega Hyper Rare"},"secret-rare":{"en":"Secre""" +
        """t Rare"},"ace-spec-rare":{"en":"ACE SPEC Rare"},"black-white-rare":{"en":"Black White Rare"},"classic-collecti""" +
        """on":{"en":"Classic Collection"},"full-art-trainer":{"en":"Full Art Trainer"},"mystery-rare":{"en":"Mystery Rar""" +
        """e"}},"subtype":{"basic":{"en":"Basic"},"stage-1":{"en":"Stage 1"},"stage-2":{"en":"Stage 2"},"baby":{"en":"Bab""" +
        """y"},"restored":{"en":"Restored"},"level-up":{"en":"Level-Up"},"break":{"en":"BREAK"},"mega":{"en":"Mega"},"vma""" +
        """x":{"en":"VMAX"},"vstar":{"en":"VSTAR"},"v-union":{"en":"V-UNION"},"item":{"en":"Item"},"supporter":{"en":"Sup""" +
        """porter"},"pokemon-tool":{"en":"Pokémon Tool"},"stadium":{"en":"Stadium"},"technical-machine":{"en":"Technical """ +
        """Machine"},"rockets-secret-machine":{"en":"Rocket's Secret Machine"},"basic-energy":{"en":"Basic Energy"},"spec""" +
        """ial-energy":{"en":"Special Energy"},"ex":{"en":"ex"},"ex-uppercase":{"en":"EX"},"gx":{"en":"GX"},"tag-team-gx"""" +
        """:{"en":"TAG TEAM-GX"},"v":{"en":"V"},"sp":{"en":"SP"},"prime":{"en":"Prime"},"legend":{"en":"LEGEND"}},"abilit""" +
        """y_kind":{"ability":{"en":"Ability"},"poke-power":{"en":"Poké-Power"},"poke-body":{"en":"Poké-Body"},"pokemon-p""" +
        """ower":{"en":"Pokémon Power"},"ancient-trait":{"en":"Ancient Trait"}}}}"""

    /** catalog/sets/ptcg-en-base01.v1.json.gz, decompressed */
    val BASE_SET: String =
        """{"schema":2,"id":"ptcg-en-base01","version":1,"publishedAt":"2026-09-13T23:48:45+00:00","series":"ptcg-en-base""" +
        """","code":"base01","name":"Base Set","kind":"expansion","releaseDate":"1999-01-09","printedTotal":102,"abbrevia""" +
        """tion":"BS","logo":"images/logos/ptcg-en-base01.a0e206.webp","cards":[{"id":"ptcg-en-base01-1","number":"1","pr""" +
        """intedNumber":"1/102","name":"Alakazam","category":"pokemon","subtypes":["stage-2"],"hp":80,"types":["psychic"]""" +
        ""","evolvesFrom":"Kadabra","retreat":3,"rarity":"rare","illustrator":"Ken Sugimori","dexNumbers":[65],"flavorTex""" +
        """t":"Its brain can outperform a supercomputer. Its intelligence quotient is said to be 5000.","image":"images/c""" +
        """ards/ptcg-en-base01/ptcg-en-base01-1.3a1ec6.webp","thumb":"images/cards/ptcg-en-base01/ptcg-en-base01-1.3a1ec6""" +
        """.thumb.webp","printings":[{"id":"ptcg-en-base01-1_holo","variant":"holo","finish":"holo"},{"id":"ptcg-en-base0""" +
        """1-1_1st-edition-holo","variant":"1st-edition-holo","edition":"1st-edition","finish":"holo"},{"id":"ptcg-en-bas""" +
        """e01-1_shadowless-holo","variant":"shadowless-holo","edition":"shadowless","finish":"holo"},{"id":"ptcg-en-base""" +
        """01-1_1999-2000-copyright-holo","variant":"1999-2000-copyright-holo","edition":"1999-2000-copyright","finish":"""" +
        """holo"}]},{"id":"ptcg-en-base01-2","number":"2","printedNumber":"2/102","name":"Blastoise","category":"pokemon"""" +
        ""","subtypes":["stage-2"],"hp":100,"types":["water"],"evolvesFrom":"Wartortle","retreat":3,"rarity":"mystery-rar""" +
        """e","illustrator":"Ken Sugimori","dexNumbers":[9],"flavorText":"A brutal Pokémon with pressurized water jets on""" +
        """ its shell. They are used for high-speed tackles.","image":"images/cards/ptcg-en-base01/ptcg-en-base01-2.ada06""" +
        """6.webp","thumb":"images/cards/ptcg-en-base01/ptcg-en-base01-2.ada066.thumb.webp","printings":[{"id":"ptcg-en-b""" +
        """ase01-2_holo","variant":"holo","finish":"holo"},{"id":"ptcg-en-base01-2_1st-edition-holo","variant":"1st-editi""" +
        """on-holo","edition":"1st-edition","finish":"holo"},{"id":"ptcg-en-base01-2_shadowless-holo","variant":"shadowle""" +
        """ss-holo","edition":"shadowless","finish":"holo"},{"id":"ptcg-en-base01-2_1999-2000-copyright-holo","variant":"""" +
        """1999-2000-copyright-holo","edition":"1999-2000-copyright","finish":"holo"}]},{"id":"ptcg-en-base01-58","number""" +
        """":"58","printedNumber":"58/102","name":"Pikachu","category":"pokemon","subtypes":["basic"],"hp":40,"types":["l""" +
        """ightning"],"retreat":1,"rarity":"common","illustrator":"Mitsuhiro Arita","dexNumbers":[25],"flavorText":"When """ +
        """several of these Pokémon gather, their electricity can cause lightning storms.","printings":[{"id":"ptcg-en-ba""" +
        """se01-58_normal","variant":"normal","finish":"normal"},{"id":"ptcg-en-base01-58_normal-poketour-99","variant":"""" +
        """normal-poketour-99","finish":"normal","stamps":["poketour-99"],"image":"images/cards/ptcg-en-base01/ptcg-en-ba""" +
        """se01-58_normal-poketour-99.3a1ec6.webp","thumb":"images/cards/ptcg-en-base01/ptcg-en-base01-58_normal-poketour""" +
        """-99.3a1ec6.thumb.webp"},{"id":"ptcg-en-base01-58_1st-edition-normal","variant":"1st-edition-normal","edition":""" +
        """"1st-edition","finish":"normal"},{"id":"ptcg-en-base01-58_1st-edition-normal-red-cheeks","variant":"1st-editio""" +
        """n-normal-red-cheeks","edition":"1st-edition","finish":"normal","error":"red-cheeks"},{"id":"ptcg-en-base01-58_""" +
        """shadowless-normal","variant":"shadowless-normal","edition":"shadowless","finish":"normal"},{"id":"ptcg-en-base""" +
        """01-58_shadowless-normal-red-cheeks","variant":"shadowless-normal-red-cheeks","edition":"shadowless","finish":"""" +
        """normal","error":"red-cheeks"},{"id":"ptcg-en-base01-58_1999-2000-copyright-normal","variant":"1999-2000-copyri""" +
        """ght-normal","edition":"1999-2000-copyright","finish":"normal"}]},{"id":"ptcg-en-base01-96","number":"96","prin""" +
        """tedNumber":"96/102","section":"Energy","name":"Double Colorless Energy","category":"energy","subtypes":["speci""" +
        """al-energy"],"rarity":"uncommon","illustrator":"Keiji Kinebuchi","printings":[{"id":"ptcg-en-base01-96_normal",""" +
        """"variant":"normal","finish":"normal"},{"id":"ptcg-en-base01-96_1st-edition-normal","variant":"1st-edition-norm""" +
        """al","edition":"1st-edition","finish":"normal"},{"id":"ptcg-en-base01-96_shadowless-normal","variant":"shadowle""" +
        """ss-normal","edition":"shadowless","finish":"normal"},{"id":"ptcg-en-base01-96_1999-2000-copyright-normal","var""" +
        """iant":"1999-2000-copyright-normal","edition":"1999-2000-copyright","finish":"normal"}]}]}"""
}
