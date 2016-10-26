console.log("map is running")
var htmlMap = $('#map')[0];
console.log('hello')
console.log(htmlMap);
var $map = L.map(htmlMap);
$map.setView([35.294351, 140.110349], 7);
new L.tileLayer("http://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png").addTo($map);
var lastLayer = new L.tileLayer("http://localhost:8080/ndvi/{z}/{x}/{y}", {layers: 'default', maxNativeZoom: 13, maxZoom: 18});
lastLayer.addTo($map);

$('.toggle-button').on('click', function () {
    $('.toggle-button').toggleClass("selected");
    var selected = $('.toggle-button').attr('class').includes('selected');
    console.log(selected);
    $map.setView([35.294351, 140.110349], 7);
    if (selected) {
        console.log("NDWI")
        $map.removeLayer(lastLayer);
        lastLayer = new L.tileLayer("http://localhost:8080/ndwi/{z}/{x}/{y}", {layers: 'default', maxNativeZoom: 13, maxZoom: 18});
        lastLayer.addTo($map);
    } else {
        console.log("NDWI")
        $map.removeLayer(lastLayer);
        lastLayer = new L.tileLayer("http://localhost:8080/ndvi/{z}/{x}/{y}", {layers: 'default', maxNativeZoom: 13, maxZoom: 18});
        lastLayer.addTo($map);
    }

});