var $map = L.map('map');
$map.setView([35.294351, 140.110349], 7);
new L.tileLayer("http://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png").addTo(map);
new L.tileLayer("http://localhost:8080/{z}/{x}/{y}", {layers: 'default', maxNativeZoom: 13, maxZoom: 18}).addTo(map);

$(document).on('click', '.toggle-button', function() {
    $(this).toggleClass('toggle-button-selected');
    var selected = $(this).attr('selected');
    $map = L.map('map');
    $map.setView([35.294351, 140.110349], 7);
    if (selected) {
        console.log("Should be NDWI")
        new L.tileLayer("http://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png").addTo(map);
        new L.tileLayer("http://localhost:8080/{z}/{x}/{y}", {layers: 'default', maxNativeZoom: 13, maxZoom: 18}).addTo(map);
    }
});