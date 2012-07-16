dm4c.add_plugin("de.deepamehta.iconpicker", function() {

    dm4c.load_stylesheet("/de.deepamehta.iconpicker/style/iconpicker.css")

    // === Webclient Listeners ===

    dm4c.add_listener("init", function() {
        dm4c.ui.dialog("iconpicker-dialog", "Choose Icon")
    })
})
