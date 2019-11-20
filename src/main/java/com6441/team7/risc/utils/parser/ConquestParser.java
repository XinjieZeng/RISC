package com6441.team7.risc.utils.parser;

import com6441.team7.risc.api.RiscConstants;
import com6441.team7.risc.api.exception.ContinentParsingException;
import com6441.team7.risc.api.exception.CountryParsingException;
import com6441.team7.risc.api.exception.MissingInfoException;
import com6441.team7.risc.api.exception.NeighborParsingException;
import com6441.team7.risc.api.model.*;
import com6441.team7.risc.utils.CommonUtils;
import com6441.team7.risc.view.GameView;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com6441.team7.risc.api.RiscConstants.COMMA;
import static com6441.team7.risc.api.RiscConstants.EOL;
import static com6441.team7.risc.api.RiscConstants.NON_EXIST;
import static java.util.Objects.isNull;

public class ConquestParser implements IConquestParser {
    private MapGraph mapGraph;
    /**
     * generates id for continents
     */
    private AtomicInteger continentIdGenerator;

    /**
     * generates id for countries
     */
    private AtomicInteger countryIdGenerator;


    public ConquestParser(AtomicInteger countryIdGenerator, AtomicInteger continentIdGenerator) {
        this.continentIdGenerator = continentIdGenerator;
        this.countryIdGenerator = countryIdGenerator;
        this.mapGraph = new MapGraph();
    }


    @Override
    public void showConquestMap(GameView view, MapService mapService) {
        view.displayMessage(getMapGraphString());
        view.displayMessage(getContinentString(mapService));
        view.displayMessage(getTerritoryString(mapService));
    }

    @Override
    public boolean saveConquestMap(String fileName, MapService mapService) {
        StringBuilder stringBuilder =
                new StringBuilder()
                        .append(getMapGraphString())
                        .append(getContinentString(mapService))
                        .append(getTerritoryString(mapService))
                        .append("\n");

        File file = new File(fileName);
        try {
            FileUtils.writeStringToFile(file, stringBuilder.toString(), StandardCharsets.UTF_8.name());
            return true;
        }catch (IOException e) {
            return false;
        }

    }

    /**
     * get maptGraph in strings
     *
     * @return
     */
    private String getMapGraphString() {

        return "[Map]" + "\n" + mapGraph.getMapGraph();
    }


    /**
     * get continents string
     *
     * @return
     */
    private String getContinentString(MapService mapService) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("[continents]");
        stringBuilder.append("\n");


        mapService.getContinents().forEach(continent -> {
            stringBuilder.append(continent.getName()).append("=");
            stringBuilder.append(continent.getContinentValue()).append(EOL);

        });

        return stringBuilder.toString();
    }

    private String getTerritoryString(MapService mapService) {
        /**
         * get string of countries
         * @return arrays of string
         */

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("[Territories]");
        stringBuilder.append("\n");


        mapService.getCountries().forEach(country -> {
            stringBuilder.append(country.getCountryName()).append(COMMA);
            stringBuilder.append(country.getCoordinateX()).append(COMMA);
            stringBuilder.append(country.getCoordinateY()).append(COMMA);
            stringBuilder.append(country.getContinentName()).append(COMMA);


            for (Map.Entry<Integer, Set<Integer>> entry : mapService.getAdjacencyCountriesMap().entrySet()) {

                if(entry.getKey().equals(country.getId())){

                    int size = 0;

                    for (Integer integer : entry.getValue()) {
                        stringBuilder.append(mapService.findCorrespondingNameByCountryId(integer).get());
                        size ++;

                        if(size != entry.getValue().size()){
                            stringBuilder.append(COMMA);
                        }
                    }

                    stringBuilder.append("\n");
                }


            }

        });


        return stringBuilder.toString();
    }



    @Override
    public void readConquestMapFile(String filename, GameView view, MapService mapService) {
        String rawFileContent = CommonUtils.readFile(filename);

        if(rawFileContent.equalsIgnoreCase(NON_EXIST)){
            createFile(filename, view);
            return;
        }
        parseFile(rawFileContent, view, mapService);
    }

    private void createFile(String fileName, GameView view) {
        File file = new File(fileName);
        try {
            FileUtils.writeStringToFile(file, "", StandardCharsets.UTF_8.name());
            view.displayMessage("a file " + file.getName() + " has been created.");
        } catch (IOException e) {
            view.displayMessage(e.getMessage());
        }
    }

    /**
     * read existing map and create continent, country and its neighbors.
     *
     * @param rawFileContent exsting map as a string
     * @return
     */
    boolean parseFile(String rawFileContent, GameView view, MapService mapService) {

        String[] parts = StringUtils.split(rawFileContent.replaceAll("\r",StringUtils.EMPTY), "[");

        try {
            if (parts.length != 3) {
                throw new MissingInfoException("The map is not valid");
            }

            parseMapGraphInfo(parts[0]);
            parseRawContinents(parts[1], mapService);
            parseRawCountries(parts[2], mapService);
            parseRawNeighboringCountries(parts[2], mapService);

        } catch (Exception e) {
            view.displayMessage(e.getMessage());
            return false;
        }

        return mapService.isStronglyConnected();

    }


    /**
     * add map graph information
     *
     * @param part
     */
    void parseMapGraphInfo(String part) {

        mapGraph.setMapGraph(StringUtils.substringAfter(part, "]\n"));
    }


    /**
     * read continent string from existing map and split it with new line,
     * for each line, call createContinentFromRaw to create new continent.
     *
     * @param part continent string
     * @return
     */
    Set<Continent> parseRawContinents(String part, MapService mapService) {
        String continentInfo = StringUtils.substringAfter(part, "]\n");

        Set<Continent> continentSet = Optional.of(StringUtils.split(continentInfo, "\n"))
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(this::createContinentFromRaw)
                .collect(Collectors.toSet());

        mapService.addContinent(continentSet);

        return continentSet;

    }

    /**
     * read continent string from the existing map file and save each valid continent to the mapService
     * if the continent is not valid, will throw an exception
     *
     * @param s continent string
     * @return
     */
    private Continent createContinentFromRaw(String s) {

        try {

            String[] continentInfo = StringUtils.split(s, RiscConstants.ASSIGNMENT);

            if (isNull(continentInfo) || continentInfo.length != 2) {
                throw new ContinentParsingException("continent: " + s + " is not valid ");
            }

            String name = convertFormat(continentInfo[0]);
            int continentValue = Integer.parseInt(continentInfo[1]);

            return new Continent(continentIdGenerator.incrementAndGet(), name, continentValue);

        } catch (NumberFormatException e) {
            throw new ContinentParsingException(e);
        }

    }

    /**
     * read country string from existing map and split it with new line,
     * for each line, call createCountryFromRaw to create new country.
     *
     * @param
     * @return
     */
    Set<Country> parseRawCountries(String part, MapService mapService) {
        String countryInfo = StringUtils.substringAfter(part, "\n");
        Set<Country> countrySet = Optional.of(StringUtils.split(countryInfo, EOL))
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(rawCountry -> createCountryFromRaw(rawCountry, mapService))
                .collect(Collectors.toSet());

        mapService.addCountry(countrySet);
        return countrySet;
    }

    /**
     * read country string from the existing map file and save each valid country to the mapService
     * if the country is not valid, will throw an exception
     *
     * @param s
     * @return
     */
    private Country createCountryFromRaw(String s, MapService mapService) {
        try {
            String[] countryInfo = StringUtils.split(s, RiscConstants.COMMA);

            if (countryInfo.length < 4) {
                throw new CountryParsingException("territories: " + s + " is not valid.");
            }

            int countryId = countryIdGenerator.incrementAndGet();
            String countryName = convertFormat(countryInfo[0]);
            int coordinateX = Integer.parseInt(countryInfo[1]);
            int coordinateY = Integer.parseInt(countryInfo[2]);
            String continentName = convertFormat(countryInfo[3]);


            if (mapService.continentNameNotExist(continentName)) {
                throw new CountryParsingException("territory: " + s + " contains invalid continent information");
            }

            int continentId = mapService.findCorrespondingIdByContinentName(continentName).get();

            Country country = new Country(countryId, countryName, continentId);
            country.setCoordinateX(coordinateX);
            country.setCoordinateY(coordinateY);
            country.setContinentName(continentName);

            return country;

        } catch (NumberFormatException e) {
            throw new CountryParsingException(e);
        }
    }

    /**
     * read strings from the existing file, save neighboring countries info in the mapServer
     *
     * @param part string for borders(neighboring countries)
     * @return
     */
    Map<Integer, Set<Integer>> parseRawNeighboringCountries(String part, MapService mapService) {

        part = StringUtils.substringAfter(part, "]\n");
        String[] adjacencyInfo = StringUtils.split(part, "\n");
        Map<Integer, Set<Integer>> adjacencyMap = new HashMap<>();

        Arrays.stream(adjacencyInfo)
                .map(info -> createAdjacencyCountriesFromRaw(info, mapService))
                .forEach(list -> {
                    int countryId = list.get(0);
                    Set<Integer> adjacencyId = new HashSet<>(list.subList(1, list.size()));
                    adjacencyMap.put(countryId, adjacencyId);
                });

        mapService.addNeighboringCountries(adjacencyMap);

        return adjacencyMap;
    }


    /**
     * read neighboring countries id
     *
     * @param s a line for neighboring countries
     * @return
     */
    private List<Integer> createAdjacencyCountriesFromRaw(String s, MapService mapService) {

        List<String> adjacency = Arrays.asList(StringUtils.split(s, COMMA));
        List<Integer> list = new ArrayList<>();

        throwWhenNoNeighboringCountry(s, adjacency);
        throwWhenNeighbouringCountriesIdInvalid(s, adjacency.subList(4, adjacency.size()), mapService);


        list.add(mapService.findCorrespondingIdByCountryName(adjacency.get(0)).get());

        for (String country : adjacency.subList(4, adjacency.size())) {
            list.add(mapService.findCorrespondingIdByCountryName(country).get());

        }
        return list;

    }

    private void throwWhenNeighbouringCountriesIdInvalid(String s, List<String> adjacency, MapService mapService) {
        for (String country : adjacency) {
            if (!mapService.findCorrespondingIdByCountryName(country).isPresent()) {
                throw new NeighborParsingException(s + " is not valid");
            }
        }
    }

    private void throwWhenNoNeighboringCountry(String s, List<String> adjacency) {
        if (adjacency.size() < 4) {
            throw new NeighborParsingException(s + " is not valid");
        }
    }


    /**
     * delete whitespace and lower cases the string
     *
     * @param name
     * @return
     */
    private String convertFormat(String name) {
        return StringUtils.deleteWhitespace(name).toLowerCase(Locale.CANADA);
    }


}